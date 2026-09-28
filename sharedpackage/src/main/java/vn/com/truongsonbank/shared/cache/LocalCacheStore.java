package vn.com.truongsonbank.shared.cache;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

class LocalCacheStore {
    private final Cache<String, CachedValue> cache;

    LocalCacheStore(CacheProperties properties) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getL1().getMaximumSize())
                .expireAfter(new Expiry<String, CachedValue>() {
                    @Override
                    public long expireAfterCreate(String key, CachedValue value, long currentTime) {
                        return value.ttlNanos();
                    }

                    @Override
                    public long expireAfterUpdate(String key, CachedValue value, long currentTime, long currentDuration) {
                        return value.ttlNanos();
                    }

                    @Override
                    public long expireAfterRead(String key, CachedValue value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    Optional<CachedValue> get(String key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    void put(String key, Object value, java.time.Duration ttl, java.time.Duration softTtl) {
        cache.put(key, CachedValue.of(
                value,
                Math.max(ttl.toNanos(), TimeUnit.MILLISECONDS.toNanos(1)),
                System.nanoTime() + Math.max(softTtl.toNanos(), TimeUnit.MILLISECONDS.toNanos(1))));
    }

    void evict(String key) {
        cache.invalidate(key);
    }

    void evictPrefix(String prefix) {
        cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }
}
