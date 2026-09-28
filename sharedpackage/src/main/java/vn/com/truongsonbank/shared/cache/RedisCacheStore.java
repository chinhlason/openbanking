package vn.com.truongsonbank.shared.cache;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class RedisCacheStore {
    private static final String NULL_VALUE = "N:";
    private static final String VALUE = "V:";
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    RedisCacheStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    Optional<CachedValue> get(String key, Type returnType) {
        String value = redis.opsForValue().get(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            if (value.startsWith(NULL_VALUE)) {
                CachePayload payload = objectMapper.readValue(value.substring(NULL_VALUE.length()), CachePayload.class);
                return Optional.of(payload.cachedValue(null));
            }
            if (!value.startsWith(VALUE)) {
                return Optional.empty();
            }
            CachePayload payload = objectMapper.readValue(value.substring(VALUE.length()), CachePayload.class);
            Object body = objectMapper.convertValue(payload.value(), objectMapper.constructType(returnType));
            return Optional.of(payload.cachedValue(body));
        } catch (JacksonException e) {
            redis.delete(key);
            return Optional.empty();
        }
    }

    void put(String key, Object value, Duration ttl, Duration softTtl) {
        try {
            CachePayload payload = new CachePayload(value, Instant.now().plus(softTtl).toEpochMilli());
            String redisValue = (value == null ? NULL_VALUE : VALUE) + objectMapper.writeValueAsString(payload);
            redis.opsForValue().set(key, redisValue, ttl);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize cache value", e);
        }
    }

    void evict(String key) {
        redis.delete(key);
    }

    void evictPrefix(String prefix) {
        // ponytail: KEYS is enough for local/dev; replace with SCAN before using large Redis keyspaces.
        Set<String> keys = redis.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private record CachePayload(Object value, long softExpireAtMillis) {
        CachedValue cachedValue(Object convertedValue) {
            long remainingNanos = Math.max(
                    Duration.between(Instant.now(), Instant.ofEpochMilli(softExpireAtMillis)).toNanos(),
                    0);
            return CachedValue.of(convertedValue, 1, System.nanoTime() + remainingNanos);
        }
    }
}
