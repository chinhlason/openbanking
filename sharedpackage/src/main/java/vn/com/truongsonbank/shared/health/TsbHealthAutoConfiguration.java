package vn.com.truongsonbank.shared.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import vn.com.truongsonbank.shared.cache.CacheAutoConfiguration;
import vn.com.truongsonbank.shared.kafka.KafkaProperties;
import vn.com.truongsonbank.shared.kafka.KafkaAutoConfiguration;
import vn.com.truongsonbank.shared.protocol.ProtocolProperties;
import vn.com.truongsonbank.shared.protocol.TsbProtocolAutoConfiguration;
import vn.com.truongsonbank.shared.protocol.TsbServiceDiscoveryClient;

@AutoConfiguration(after = {CacheAutoConfiguration.class, KafkaAutoConfiguration.class, TsbProtocolAutoConfiguration.class})
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "tsb.shared.health", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(HealthProperties.class)
public class TsbHealthAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "tsbSharedHealthIndicator")
    HealthIndicator tsbSharedHealthIndicator() {
        return new TsbSharedHealthIndicator();
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(name = "tsbRedisHealthIndicator")
    HealthIndicator tsbRedisHealthIndicator(StringRedisTemplate redis) {
        return new TsbRedisHealthIndicator(redis);
    }

    @Bean
    @ConditionalOnClass(AdminClient.class)
    @ConditionalOnBean(KafkaProperties.class)
    @ConditionalOnMissingBean(name = "tsbKafkaHealthIndicator")
    HealthIndicator tsbKafkaHealthIndicator(KafkaProperties properties, HealthProperties healthProperties) {
        return new TsbKafkaHealthIndicator(properties, healthProperties);
    }

    @Bean
    @ConditionalOnBean(ProtocolProperties.class)
    @ConditionalOnMissingBean(name = "tsbProtocolHealthIndicator")
    HealthIndicator tsbProtocolHealthIndicator(
            ProtocolProperties properties,
            ObjectProvider<TsbServiceDiscoveryClient> discoveryClient,
            HealthProperties healthProperties) {
        return new TsbProtocolHealthIndicator(properties, discoveryClient.getIfAvailable(), healthProperties);
    }
}
