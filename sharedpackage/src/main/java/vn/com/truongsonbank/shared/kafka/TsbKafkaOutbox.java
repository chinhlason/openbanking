package vn.com.truongsonbank.shared.kafka;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class TsbKafkaOutbox {
    private final ObjectProvider<DataSource> dataSource;
    private final ObjectMapper objectMapper;
    private final KafkaProperties.Outbox properties;
    private final TsbKafkaOutboxSchema schema;
    private final ObjectProvider<Tracer> tracer;

    TsbKafkaOutbox(
            ObjectProvider<DataSource> dataSource,
            ObjectMapper objectMapper,
            KafkaProperties.Outbox properties,
            TsbKafkaOutboxSchema schema,
            ObjectProvider<Tracer> tracer) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.schema = schema;
        this.tracer = tracer;
    }

    public String save(String topic, String key, Object payload) {
        return save(topic, key, payload, currentHeaders());
    }

    public String save(String topic, String key, Object payload, Map<String, String> headers) {
        DataSource source = dataSource.getIfAvailable();
        if (source == null) {
            throw new IllegalStateException("Kafka outbox requires a DataSource");
        }
        if (!schema.isReady()) {
            throw new IllegalStateException("Kafka outbox table " + properties.getTableName() + " is not ready");
        }
        String id = UUID.randomUUID().toString();
        new JdbcTemplate(source).update("""
                        insert into %s
                        (id, topic, event_key, payload, headers, status, retry_count, created_at)
                        values (?, ?, ?, ?, ?, 'PENDING', 0, ?)
                        """.formatted(properties.getTableName()),
                id,
                topic,
                key,
                json(payload),
                json(headers),
                Instant.now());
        return id;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Kafka outbox payload is not JSON serializable", ex);
        }
    }

    private Map<String, String> currentHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        copyInbound(headers, "traceparent");
        copyInbound(headers, "tracestate");
        copyInbound(headers, "baggage");
        copyInbound(headers, "idempotency-key");
        copyInbound(headers, "x-request-id");
        copyInbound(headers, "x-correlation-id");
        addTraceparent(headers);
        return headers;
    }

    private void copyInbound(Map<String, String> headers, String name) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            String value = servletRequestAttributes.getRequest().getHeader(name);
            if (value != null && !value.isBlank()) {
                headers.put(name, value);
            }
        }
    }

    private void addTraceparent(Map<String, String> headers) {
        if (headers.containsKey("traceparent")) {
            return;
        }
        Tracer currentTracer = tracer.getIfAvailable();
        Span span = currentTracer == null ? null : currentTracer.currentSpan();
        if (span != null) {
            headers.put("traceparent", "00-" + span.context().traceId() + "-" + span.context().spanId() + "-01");
        }
    }
}
