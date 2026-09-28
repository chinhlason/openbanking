package vn.com.truongsonbank.shared.cache;

import org.springframework.data.redis.core.StringRedisTemplate;

class RedisCacheInvalidationPublisher {
    private final StringRedisTemplate redis;
    private final CacheProperties properties;

    RedisCacheInvalidationPublisher(StringRedisTemplate redis, CacheProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    void evict(String key) {
        publish(CacheInvalidationEvent.key(key));
    }

    void evictPrefix(String prefix) {
        publish(CacheInvalidationEvent.prefix(prefix));
    }

    private void publish(CacheInvalidationEvent event) {
        if (properties.getInvalidation().isEnabled()) {
            redis.convertAndSend(properties.getInvalidation().getChannel(), event.encode());
        }
    }
}
