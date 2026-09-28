package vn.com.truongsonbank.shared.kafka;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tsb.shared.kafka")
public class KafkaProperties {
    private boolean enabled = true;
    private List<String> bootstrapServers = List.of("localhost:9092");
    private String clientId = "tsb-service";
    private String groupId = "tsb-service";
    private String trustedPackages = "*";
    private Retry retry = new Retry();
    private Dlq dlq = new Dlq();
    private Consumer consumer = new Consumer();
    private Security security = new Security();
    private Transaction transaction = new Transaction();
    private Outbox outbox = new Outbox();
    private Map<String, Topic> topics = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(List<String> bootstrapServers) {
        this.bootstrapServers = bootstrapServers == null || bootstrapServers.isEmpty()
                ? List.of("localhost:9092")
                : List.copyOf(bootstrapServers);
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getTrustedPackages() {
        return trustedPackages;
    }

    public void setTrustedPackages(String trustedPackages) {
        this.trustedPackages = trustedPackages;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry == null ? new Retry() : retry;
    }

    public Dlq getDlq() {
        return dlq;
    }

    public void setDlq(Dlq dlq) {
        this.dlq = dlq == null ? new Dlq() : dlq;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer == null ? new Consumer() : consumer;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security == null ? new Security() : security;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction == null ? new Transaction() : transaction;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public void setOutbox(Outbox outbox) {
        this.outbox = outbox == null ? new Outbox() : outbox;
    }

    public Map<String, Topic> getTopics() {
        return topics;
    }

    public void setTopics(Map<String, Topic> topics) {
        this.topics = topics == null ? new LinkedHashMap<>() : new LinkedHashMap<>(topics);
    }

    public static class Retry {
        private long attempts = 3;
        private Duration backoff = Duration.ofMillis(500);

        public long getAttempts() {
            return attempts;
        }

        public void setAttempts(long attempts) {
            this.attempts = attempts;
        }

        public Duration getBackoff() {
            return backoff;
        }

        public void setBackoff(Duration backoff) {
            this.backoff = backoff;
        }
    }

    public static class Dlq {
        private boolean enabled = true;
        private String suffix = ".DLQ";
        private boolean monitorEnabled = true;
        private Duration monitorInterval = Duration.ofSeconds(30);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSuffix() {
            return suffix;
        }

        public void setSuffix(String suffix) {
            this.suffix = suffix;
        }

        public boolean isMonitorEnabled() {
            return monitorEnabled;
        }

        public void setMonitorEnabled(boolean monitorEnabled) {
            this.monitorEnabled = monitorEnabled;
        }

        public Duration getMonitorInterval() {
            return monitorInterval;
        }

        public void setMonitorInterval(Duration monitorInterval) {
            this.monitorInterval = monitorInterval;
        }
    }

    public static class Consumer {
        private String autoOffsetReset = "latest";

        public String getAutoOffsetReset() {
            return autoOffsetReset;
        }

        public void setAutoOffsetReset(String autoOffsetReset) {
            this.autoOffsetReset = autoOffsetReset;
        }
    }

    public static class Security {
        private boolean enabled;
        private Map<String, String> properties = new LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(properties);
        }
    }

    public static class Transaction {
        private boolean enabled;
        private String transactionIdPrefix = "tsb-tx-";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTransactionIdPrefix() {
            return transactionIdPrefix;
        }

        public void setTransactionIdPrefix(String transactionIdPrefix) {
            this.transactionIdPrefix = transactionIdPrefix;
        }
    }

    public static class Outbox {
        private boolean enabled;
        private boolean relayEnabled = true;
        private boolean autoCreateTable = true;
        private String tableName = "tsb_outbox_event";
        private int batchSize = 20;
        private long pollIntervalMs = 1000;
        private int maxAttempts = 5;
        private Duration retryBackoff = Duration.ofSeconds(10);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRelayEnabled() {
            return relayEnabled;
        }

        public void setRelayEnabled(boolean relayEnabled) {
            this.relayEnabled = relayEnabled;
        }

        public boolean isAutoCreateTable() {
            return autoCreateTable;
        }

        public void setAutoCreateTable(boolean autoCreateTable) {
            this.autoCreateTable = autoCreateTable;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getRetryBackoff() {
            return retryBackoff;
        }

        public void setRetryBackoff(Duration retryBackoff) {
            this.retryBackoff = retryBackoff;
        }
    }

    public static class Topic {
        private int partitions = 1;
        private short replicas = 1;
        private boolean ordered;

        public int getPartitions() {
            return partitions;
        }

        public void setPartitions(int partitions) {
            this.partitions = partitions;
        }

        public short getReplicas() {
            return replicas;
        }

        public void setReplicas(short replicas) {
            this.replicas = replicas;
        }

        public boolean isOrdered() {
            return ordered;
        }

        public void setOrdered(boolean ordered) {
            this.ordered = ordered;
        }
    }
}
