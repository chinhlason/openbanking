package vn.com.truongsonbank.shared.kafka;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;

class TsbKafkaOutboxRelayWorker implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(TsbKafkaOutboxRelayWorker.class);

    private final ObjectProvider<DataSource> dataSource;
    private final ObjectMapper objectMapper;
    private final KafkaProperties.Outbox properties;
    private final TsbKafkaOutboxSchema schema;
    private final TsbKafkaPublisher publisher;
    private final KafkaInstrumentation instrumentation;
    private ScheduledExecutorService executor;
    private volatile boolean running;

    TsbKafkaOutboxRelayWorker(
            ObjectProvider<DataSource> dataSource,
            ObjectMapper objectMapper,
            KafkaProperties.Outbox properties,
            TsbKafkaOutboxSchema schema,
            TsbKafkaPublisher publisher,
            KafkaInstrumentation instrumentation) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.schema = schema;
        this.publisher = publisher;
        this.instrumentation = instrumentation;
    }

    @Override
    public void start() {
        if (!properties.isRelayEnabled()) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "tsb-kafka-outbox-relay"));
        executor.scheduleWithFixedDelay(this::pollSafely, properties.getPollIntervalMs(),
                properties.getPollIntervalMs(), TimeUnit.MILLISECONDS);
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void pollSafely() {
        try {
            poll();
        } catch (Exception ex) {
            log.warn("Kafka outbox relay poll failed: {}", ex.getMessage(), ex);
        }
    }

    private void poll() {
        DataSource source = dataSource.getIfAvailable();
        if (source == null || !schema.isReady()) {
            return;
        }
        JdbcTemplate jdbc = new JdbcTemplate(source);
        List<OutboxEvent> events = jdbc.query("""
                        select id, topic, event_key, payload, headers, retry_count
                        from %s
                        where status = 'PENDING'
                          and (next_retry_at is null or next_retry_at <= ?)
                        order by created_at
                        limit ?
                        """.formatted(properties.getTableName()),
                (rs, rowNum) -> map(rs),
                Timestamp.from(Instant.now()),
                Math.max(properties.getBatchSize(), 1));
        for (OutboxEvent event : events) {
            relay(jdbc, event);
        }
    }

    private OutboxEvent map(ResultSet rs) throws java.sql.SQLException {
        return new OutboxEvent(
                rs.getString("id"),
                rs.getString("topic"),
                rs.getString("event_key"),
                rs.getString("payload"),
                rs.getString("headers"),
                rs.getInt("retry_count"));
    }

    private void relay(JdbcTemplate jdbc, OutboxEvent event) {
        int claimed = jdbc.update("""
                        update %s
                        set status = 'PROCESSING'
                        where id = ? and status = 'PENDING'
                        """.formatted(properties.getTableName()),
                event.id());
        if (claimed == 0) {
            return;
        }

        long start = System.nanoTime();
        try {
            publisher.send(event.topic(), event.key(), payload(event.payload()), headers(event.headers())).join();
            jdbc.update("""
                            update %s
                            set status = 'SENT', sent_at = ?
                            where id = ?
                            """.formatted(properties.getTableName()),
                    Timestamp.from(Instant.now()),
                    event.id());
            instrumentation.recordOutboxRelay(event.topic(), "success", Duration.ofNanos(System.nanoTime() - start));
            instrumentation.count("outbox.relay", event.topic(), "success");
        } catch (Exception ex) {
            int retryCount = event.retryCount() + 1;
            boolean retry = retryCount < properties.getMaxAttempts();
            jdbc.update("""
                            update %s
                            set status = ?, retry_count = ?, next_retry_at = ?
                            where id = ?
                            """.formatted(properties.getTableName()),
                    retry ? "PENDING" : "FAILED",
                    retryCount,
                    retry ? Timestamp.from(Instant.now().plus(properties.getRetryBackoff())) : null,
                    event.id());
            instrumentation.recordOutboxRelay(event.topic(), "error", Duration.ofNanos(System.nanoTime() - start));
            instrumentation.count("outbox.relay", event.topic(), "error", ex.getClass().getSimpleName());
        }
    }

    private Object payload(String json) throws Exception {
        return objectMapper.readValue(json, Object.class);
    }

    private Map<String, String> headers(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(json, new TypeReference<>() {
        });
    }

    private record OutboxEvent(String id, String topic, String key, String payload, String headers, int retryCount) {
    }
}
