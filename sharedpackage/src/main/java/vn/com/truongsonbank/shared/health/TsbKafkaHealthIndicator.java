package vn.com.truongsonbank.shared.health;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import vn.com.truongsonbank.shared.kafka.KafkaProperties;

class TsbKafkaHealthIndicator extends AbstractHealthIndicator {
    private final KafkaProperties properties;
    private final HealthProperties healthProperties;

    TsbKafkaHealthIndicator(KafkaProperties properties, HealthProperties healthProperties) {
        this.properties = properties;
        this.healthProperties = healthProperties;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        try (AdminClient admin = AdminClient.create(config())) {
            DescribeClusterResult cluster = admin.describeCluster();
            long timeout = healthProperties.getTimeout().toMillis();
            builder.up()
                    .withDetail("clusterId", cluster.clusterId().get(timeout, TimeUnit.MILLISECONDS))
                    .withDetail("nodeCount", cluster.nodes().get(timeout, TimeUnit.MILLISECONDS).size());
        }
    }

    private Map<String, Object> config() {
        Map<String, Object> config = new HashMap<>();
        config.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        config.put(CommonClientConfigs.CLIENT_ID_CONFIG, properties.getClientId() + "-health");
        if (properties.getSecurity().isEnabled()) {
            config.putAll(properties.getSecurity().getProperties());
        }
        return config;
    }
}
