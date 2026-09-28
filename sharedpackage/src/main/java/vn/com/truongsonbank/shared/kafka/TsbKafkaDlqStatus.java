package vn.com.truongsonbank.shared.kafka;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.ConsumerFactory;

public class TsbKafkaDlqStatus {
    private final ConsumerFactory<String, Object> consumerFactory;
    private final KafkaProperties.Dlq properties;

    TsbKafkaDlqStatus(ConsumerFactory<String, Object> consumerFactory, KafkaProperties.Dlq properties) {
        this.consumerFactory = consumerFactory;
        this.properties = properties;
    }

    public List<DlqTopicStatus> all() {
        try (Consumer<String, Object> consumer = consumerFactory.createConsumer()) {
            return consumer.listTopics(Duration.ofSeconds(5)).keySet().stream()
                    .filter(topic -> topic.endsWith(properties.getSuffix()))
                    .sorted()
                    .map(topic -> topic(topic, consumer))
                    .toList();
        }
    }

    public DlqTopicStatus topic(String topic) {
        try (Consumer<String, Object> consumer = consumerFactory.createConsumer()) {
            return topic(topic, consumer);
        }
    }

    private DlqTopicStatus topic(String topic, Consumer<String, Object> consumer) {
        List<PartitionInfo> partitions = consumer.partitionsFor(topic, Duration.ofSeconds(5));
        if (partitions == null || partitions.isEmpty()) {
            return new DlqTopicStatus(topic, 0, List.of());
        }
        List<TopicPartition> topicPartitions = partitions.stream()
                .map(partition -> new TopicPartition(topic, partition.partition()))
                .toList();
        Map<TopicPartition, Long> beginning = consumer.beginningOffsets(topicPartitions);
        Map<TopicPartition, Long> end = consumer.endOffsets(topicPartitions);
        List<DlqPartitionStatus> statuses = new ArrayList<>();
        long depth = 0;
        for (TopicPartition partition : topicPartitions) {
            long start = beginning.getOrDefault(partition, 0L);
            long latest = end.getOrDefault(partition, 0L);
            long partitionDepth = Math.max(latest - start, 0);
            depth += partitionDepth;
            statuses.add(new DlqPartitionStatus(partition.partition(), start, latest, partitionDepth));
        }
        return new DlqTopicStatus(topic, depth, statuses);
    }

    public record DlqTopicStatus(String topic, long depth, List<DlqPartitionStatus> partitions) {
    }

    public record DlqPartitionStatus(int partition, long beginningOffset, long endOffset, long depth) {
    }
}
