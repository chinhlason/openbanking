package vn.com.truongsonbank.shared.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

public class TsbKafkaDlqReplay {
    private final ConsumerFactory<String, Object> consumerFactory;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaProperties.Dlq properties;
    private final KafkaInstrumentation instrumentation;

    TsbKafkaDlqReplay(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaProperties.Dlq properties,
            KafkaInstrumentation instrumentation) {
        this.consumerFactory = consumerFactory;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.instrumentation = instrumentation;
    }

    public ReplayResult replay(String dlqTopic, int partition, long offset) {
        String targetTopic = targetTopic(dlqTopic);
        long start = System.nanoTime();
        try (Consumer<String, Object> consumer = consumerFactory.createConsumer("tsb-dlq-replay-" + UUID.randomUUID(), null)) {
            TopicPartition topicPartition = new TopicPartition(dlqTopic, partition);
            consumer.assign(java.util.List.of(topicPartition));
            consumer.seek(topicPartition, offset);
            ConsumerRecord<String, Object> record = consumer.poll(Duration.ofSeconds(3))
                    .records(topicPartition)
                    .stream()
                    .filter(item -> item.offset() == offset)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("DLQ record not found: " + dlqTopic + "-" + partition + "@" + offset));
            SendResult<String, Object> result = kafkaTemplate.send(replayRecord(record, targetTopic)).join();
            instrumentation.count("dlq.replay", targetTopic, "success");
            instrumentation.recordDlqReplay(targetTopic, "success", Duration.ofNanos(System.nanoTime() - start));
            return new ReplayResult(dlqTopic, partition, offset, targetTopic,
                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
        } catch (RuntimeException ex) {
            instrumentation.count("dlq.replay", targetTopic, "error", ex.getClass().getSimpleName());
            instrumentation.recordDlqReplay(targetTopic, "error", Duration.ofNanos(System.nanoTime() - start));
            throw ex;
        }
    }

    private ProducerRecord<String, Object> replayRecord(ConsumerRecord<String, Object> record, String targetTopic) {
        ProducerRecord<String, Object> replay = new ProducerRecord<>(targetTopic, record.key(), record.value());
        for (Header header : record.headers()) {
            replay.headers().add(header);
        }
        putHeader(replay, "x-dlq-replayed", "true");
        putHeader(replay, "x-dlq-replayed-at", Instant.now().toString());
        putHeader(replay, "x-dlq-source-topic", record.topic());
        putHeader(replay, "x-dlq-source-partition", String.valueOf(record.partition()));
        putHeader(replay, "x-dlq-source-offset", String.valueOf(record.offset()));
        return replay;
    }

    private void putHeader(ProducerRecord<String, Object> record, String name, String value) {
        record.headers().remove(name);
        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private String targetTopic(String dlqTopic) {
        String suffix = properties.getSuffix();
        if (dlqTopic == null || suffix == null || !dlqTopic.endsWith(suffix)) {
            throw new IllegalArgumentException("DLQ topic must end with " + suffix);
        }
        return dlqTopic.substring(0, dlqTopic.length() - suffix.length());
    }

    public record ReplayResult(
            String dlqTopic,
            int dlqPartition,
            long dlqOffset,
            String targetTopic,
            int targetPartition,
            long targetOffset) {
    }
}
