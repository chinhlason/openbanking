package vn.com.truongsonbank.shared.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

public class TsbKafkaPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaInstrumentation instrumentation;
    private final ObjectProvider<Tracer> tracer;

    TsbKafkaPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaInstrumentation instrumentation,
            ObjectProvider<Tracer> tracer) {
        this.kafkaTemplate = kafkaTemplate;
        this.instrumentation = instrumentation;
        this.tracer = tracer;
    }

    public CompletableFuture<SendResult<String, Object>> send(String topic, String key, Object payload) {
        return send(topic, key, payload, Map.of());
    }

    public CompletableFuture<SendResult<String, Object>> send(
            String topic,
            String key,
            Object payload,
            Map<String, String> headers) {
        long startNanos = System.nanoTime();
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, payload);
        addHeaders(record, headers);
        addTraceHeader(record);
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(record);
        future.whenComplete((result, error) -> {
            String outcome = error == null ? "success" : "error";
            instrumentation.recordPublish(topic, outcome, Duration.ofNanos(System.nanoTime() - startNanos));
            instrumentation.count("publish", topic, outcome, error == null ? "none" : error.getClass().getSimpleName());
        });
        return future;
    }

    private void addHeaders(ProducerRecord<String, Object> record, Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        headers.forEach((name, value) -> {
            if (name != null && !name.isBlank() && value != null) {
                record.headers().remove(name);
                record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
            }
        });
    }

    private void addTraceHeader(ProducerRecord<String, Object> record) {
        Tracer currentTracer = tracer.getIfAvailable();
        Span span = currentTracer == null ? null : currentTracer.currentSpan();
        if (span == null) {
            return;
        }
        if (record.headers().lastHeader("traceparent") == null) {
            String traceparent = "00-" + span.context().traceId() + "-" + span.context().spanId() + "-01";
            record.headers().add("traceparent", traceparent.getBytes(StandardCharsets.UTF_8));
        }
    }
}
