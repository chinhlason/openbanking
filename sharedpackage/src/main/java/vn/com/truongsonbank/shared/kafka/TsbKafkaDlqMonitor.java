package vn.com.truongsonbank.shared.kafka;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

class TsbKafkaDlqMonitor implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(TsbKafkaDlqMonitor.class);

    private final TsbKafkaDlqStatus status;
    private final KafkaInstrumentation instrumentation;
    private final KafkaProperties.Dlq properties;
    private ScheduledExecutorService executor;
    private volatile boolean running;

    TsbKafkaDlqMonitor(TsbKafkaDlqStatus status, KafkaInstrumentation instrumentation, KafkaProperties.Dlq properties) {
        this.status = status;
        this.instrumentation = instrumentation;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (!properties.isMonitorEnabled()) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "tsb-kafka-dlq-monitor"));
        executor.scheduleWithFixedDelay(this::pollSafely, 0,
                Math.max(properties.getMonitorInterval().toMillis(), 1000), TimeUnit.MILLISECONDS);
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
            for (TsbKafkaDlqStatus.DlqTopicStatus item : status.all()) {
                instrumentation.recordDlqDepth(item.topic(), item.depth());
            }
        } catch (Exception ex) {
            log.debug("Kafka DLQ monitor failed: {}", ex.getMessage(), ex);
        }
    }
}
