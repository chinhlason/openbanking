package vn.com.truongsonbank.shared.kafka;

import java.util.function.BiFunction;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

class TsbKafkaDeadLetterPublishingRecoverer extends DeadLetterPublishingRecoverer {
    private final KafkaInstrumentation instrumentation;

    TsbKafkaDeadLetterPublishingRecoverer(
            KafkaOperations<?, ?> template,
            BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> destinationResolver,
            KafkaInstrumentation instrumentation) {
        super(template, destinationResolver);
        this.instrumentation = instrumentation;
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, Consumer<?, ?> consumer, Exception exception) {
        super.accept(record, consumer, exception);
        instrumentation.count("dlq.publish", record.topic(), "success",
                exception == null ? "none" : exception.getClass().getSimpleName());
    }
}
