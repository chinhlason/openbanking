package vn.com.truongsonbank.shared.kafka;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

class KafkaInstrumentation {
    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicLong> dlqDepth = new ConcurrentHashMap<>();

    KafkaInstrumentation(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void recordPublish(String topic, String outcome, Duration duration) {
        Timer.builder("tsb.kafka.publish.duration")
                .description("Duration of Kafka publish operations")
                .tag("topic", topic)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(duration);
    }

    void count(String metric, String topic, String outcome) {
        meterRegistry.counter("tsb.kafka." + metric,
                "topic", topic,
                "outcome", outcome).increment();
    }

    void count(String metric, String topic, String outcome, String exception) {
        meterRegistry.counter("tsb.kafka." + metric,
                "topic", topic,
                "outcome", outcome,
                "exception", exception).increment();
    }

    void recordConsume(String topic, String outcome, Duration duration) {
        Timer.builder("tsb.kafka.consume.duration")
                .description("Duration of Kafka consumer listener processing")
                .tag("topic", topic)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(duration);
    }

    void recordOutboxRelay(String topic, String outcome, Duration duration) {
        Timer.builder("tsb.kafka.outbox.relay.duration")
                .description("Duration of Kafka outbox relay publish operations")
                .tag("topic", topic)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(duration);
    }

    void recordDlqReplay(String topic, String outcome, Duration duration) {
        Timer.builder("tsb.kafka.dlq.replay.duration")
                .description("Duration of Kafka DLQ replay operations")
                .tag("topic", topic)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(duration);
    }

    void recordDlqDepth(String topic, long depth) {
        dlqDepth.computeIfAbsent(topic, item -> {
            AtomicLong value = new AtomicLong();
            Gauge.builder("tsb.kafka.dlq.depth", value, AtomicLong::get)
                    .description("Current Kafka DLQ topic depth based on end offset minus beginning offset")
                    .tag("topic", item)
                    .register(meterRegistry);
            return value;
        }).set(depth);
    }
}
