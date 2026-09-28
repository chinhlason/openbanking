package vn.com.truongsonbank.client2;

import java.util.Map;
import java.util.stream.StreamSupport;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import vn.com.truongsonbank.shared.kafka.TsbKafkaIdempotent;

@Component
class Client2KafkaDemoListener {
    private static final Logger log = LoggerFactory.getLogger(Client2KafkaDemoListener.class);
    private final AtomicInteger consumed = new AtomicInteger();
    private final AtomicInteger dlq = new AtomicInteger();

    @TsbKafkaIdempotent
    @KafkaListener(topics = "tsb.demo.events")
    public void onDemoEvent(Map<String, Object> payload, ConsumerRecord<String, Object> record) throws InterruptedException {
        int count = consumed.incrementAndGet();
        Thread.sleep(15000);
        log.info("kafka_demo_event topic={} key={} offset={} count={} headers={} payload={}",
                record.topic(), record.key(), record.offset(), count, headers(record), payload);
        if (Boolean.TRUE.equals(payload.get("fail"))) {
            throw new IllegalStateException("Demo Kafka failure");
        }
    }

    @KafkaListener(topics = "tsb.demo.events.DLQ")
    void onDemoEventDlq(Map<String, Object> payload, ConsumerRecord<String, Object> record) {
        int count = dlq.incrementAndGet();
        log.info("kafka_demo_dlq topic={} key={} offset={} count={} payload={}",
                record.topic(), record.key(), record.offset(), count, payload);
    }

    private Map<String, String> headers(ConsumerRecord<String, Object> record) {
        return StreamSupport.stream(record.headers().spliterator(), false)
                .collect(java.util.stream.Collectors.toMap(
                        header -> header.key(),
                        header -> new String(header.value(), java.nio.charset.StandardCharsets.UTF_8),
                        (left, right) -> right));
    }
}
