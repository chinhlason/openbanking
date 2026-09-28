package vn.com.truongsonbank.shared.sequence;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@EnableConfigurationProperties(SequenceProperties.class)
@ConditionalOnProperty(prefix = "tsb.shared.sequence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SequenceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public TsbSequenceGenerator tsbSequenceGenerator(
            SequenceProperties properties,
            ObjectProvider<StringRedisTemplate> redis) {
        if ("provider".equalsIgnoreCase(properties.getMode())
                && "redis".equalsIgnoreCase(properties.getProvider())) {
            StringRedisTemplate redisTemplate = redis.getIfAvailable();
            if (redisTemplate == null) {
                throw new IllegalStateException("Redis provider requires StringRedisTemplate");
            }
            return new TsbRedisSequenceGenerator(properties, redisTemplate);
        }
        return new TsbSelfSequenceGenerator(properties);
    }
}
