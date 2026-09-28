package vn.com.truongsonbank.shared.cache;

import java.util.Optional;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration
@ConditionalOnClass(Aspect.class)
@EnableConfigurationProperties(CacheProperties.class)
@ConditionalOnProperty(prefix = "tsb.shared.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CacheAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    CacheKeyBuilder cacheKeyBuilder(CacheProperties properties) {
        return new CacheKeyBuilder(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "tsb.shared.cache.l1", name = "enabled", havingValue = "true", matchIfMissing = true)
    LocalCacheStore localCacheStore(CacheProperties properties) {
        return new LocalCacheStore(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "tsb.shared.cache.l2", name = "enabled", havingValue = "true")
    RedisCacheStore redisCacheStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        return new RedisCacheStore(redis, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    CacheInstrumentation cacheInstrumentation(
            ObjectProvider<MeterRegistry> meterRegistry,
            ObjectProvider<Tracer> tracer) {
        return new CacheInstrumentation(meterRegistry, tracer);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnProperty(prefix = "tsb.shared.cache.l2", name = "enabled", havingValue = "true")
    RedisMessageListenerContainer tsbRedisCacheMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "tsb.shared.cache.invalidation", name = "enabled", havingValue = "true", matchIfMissing = true)
    RedisCacheInvalidationPublisher redisCacheInvalidationPublisher(
            StringRedisTemplate redis,
            CacheProperties properties) {
        return new RedisCacheInvalidationPublisher(redis, properties);
    }

    @Bean
    @ConditionalOnBean({LocalCacheStore.class, RedisMessageListenerContainer.class})
    @ConditionalOnProperty(prefix = "tsb.shared.cache.invalidation", name = "enabled", havingValue = "true", matchIfMissing = true)
    RedisCacheInvalidationListener redisCacheInvalidationListener(
            RedisMessageListenerContainer container,
            LocalCacheStore localCache,
            CacheProperties properties) {
        RedisCacheInvalidationListener listener = new RedisCacheInvalidationListener(localCache);
        container.addMessageListener(listener, new ChannelTopic(properties.getInvalidation().getChannel()));
        return listener;
    }

    @Bean
    @ConditionalOnBean({LocalCacheStore.class, RedisMessageListenerContainer.class})
    @ConditionalOnProperty(prefix = "tsb.shared.cache.tracking", name = "enabled", havingValue = "true")
    RedisCacheTrackingListener redisCacheTrackingListener(
            RedisMessageListenerContainer container,
            LocalCacheStore localCache,
            CacheProperties properties,
            StringRedisTemplate redis) {
        if (properties.getTracking().isConfigureNotifyKeyspaceEvents()) {
            try (RedisConnection connection = redis.getConnectionFactory().getConnection()) {
                connection.serverCommands().setConfig("notify-keyspace-events", "Egx");
            }
        }
        RedisCacheTrackingListener listener = new RedisCacheTrackingListener(localCache, properties);
        container.addMessageListener(listener, new PatternTopic("__keyevent@*__:set"));
        container.addMessageListener(listener, new PatternTopic("__keyevent@*__:del"));
        container.addMessageListener(listener, new PatternTopic("__keyevent@*__:expired"));
        return listener;
    }

    @Bean
    @ConditionalOnMissingBean
    TsbCacheAspect tsbCacheAspect(
            CacheProperties properties,
            CacheKeyBuilder keyBuilder,
            ObjectProvider<LocalCacheStore> localCache,
            ObjectProvider<RedisCacheStore> redisCache,
            ObjectProvider<RedisCacheInvalidationPublisher> invalidationPublisher,
            CacheInstrumentation instrumentation) {
        return new TsbCacheAspect(
                properties,
                keyBuilder,
                Optional.ofNullable(localCache.getIfAvailable()),
                Optional.ofNullable(redisCache.getIfAvailable()),
                Optional.ofNullable(invalidationPublisher.getIfAvailable()),
                instrumentation);
    }
}
