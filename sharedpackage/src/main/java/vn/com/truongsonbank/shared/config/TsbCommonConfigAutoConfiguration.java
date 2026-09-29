package vn.com.truongsonbank.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@EnableConfigurationProperties(TsbCommonConfigProperties.class)
@ConditionalOnProperty(prefix = "tsb.shared.common-config", name = "enabled", havingValue = "true")
public class TsbCommonConfigAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public TsbCommonConfigClient tsbCommonConfigClient(
            TsbCommonConfigProperties properties,
            ObjectProvider<RedisConnectionFactory> redisConnectionFactory,
            ObjectProvider<ObjectMapper> objectMapper) {
        return new TsbCommonConfigClient(
                properties,
                redisConnectionFactory.getIfAvailable(),
                objectMapper.getIfAvailable(ObjectMapper::new));
    }
}
