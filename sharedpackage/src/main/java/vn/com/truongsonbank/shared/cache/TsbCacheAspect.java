package vn.com.truongsonbank.shared.cache;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

@Aspect
class TsbCacheAspect {
    private final CacheProperties properties;
    private final CacheKeyBuilder keyBuilder;
    private final Optional<LocalCacheStore> localCache;
    private final Optional<RedisCacheStore> redisCache;
    private final Optional<RedisCacheInvalidationPublisher> invalidationPublisher;
    private final CacheInstrumentation instrumentation;

    TsbCacheAspect(
            CacheProperties properties,
            CacheKeyBuilder keyBuilder,
            Optional<LocalCacheStore> localCache,
            Optional<RedisCacheStore> redisCache,
            Optional<RedisCacheInvalidationPublisher> invalidationPublisher,
            CacheInstrumentation instrumentation) {
        this.properties = properties;
        this.keyBuilder = keyBuilder;
        this.localCache = localCache;
        this.redisCache = redisCache;
        this.invalidationPublisher = invalidationPublisher;
        this.instrumentation = instrumentation;
    }

    @Around("@annotation(cacheable)")
    Object cacheable(ProceedingJoinPoint joinPoint, TsbCacheable cacheable) throws Throwable {
        String key = keyBuilder.key(cacheable.cacheName(), cacheable.key(), joinPoint);
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        CacheLookupResult lookup = instrumentation.record("lookup", cacheable.cacheName(), () -> lookup(key, method));
        if (lookup.hit()) {
            return lookup.value();
        }

        Object value = joinPoint.proceed();
        instrumentedPut(cacheable.cacheName(), key, value);
        return value;
    }

    @Around("@annotation(cachePut)")
    Object cachePut(ProceedingJoinPoint joinPoint, TsbCachePut cachePut) throws Throwable {
        Object value = joinPoint.proceed();
        String key = keyBuilder.key(cachePut.cacheName(), cachePut.key(), joinPoint);
        instrumentedPut(cachePut.cacheName(), key, value);
        publishEvict(cachePut.cacheName(), key);
        return value;
    }

    @Around("@annotation(cacheEvict)")
    Object cacheEvict(ProceedingJoinPoint joinPoint, TsbCacheEvict cacheEvict) throws Throwable {
        Object value = joinPoint.proceed();
        if (cacheEvict.allEntries()) {
            String prefix = keyBuilder.prefix(cacheEvict.cacheName());
            instrumentation.record("evict.prefix", cacheEvict.cacheName(), () -> {
                localCache.ifPresent(cache -> cache.evictPrefix(prefix));
                redisCache.ifPresent(cache -> cache.evictPrefix(prefix));
                return null;
            });
            publishEvictPrefix(cacheEvict.cacheName(), prefix);
            return value;
        }
        String key = keyBuilder.key(cacheEvict.cacheName(), cacheEvict.key(), joinPoint);
        instrumentation.record("evict", cacheEvict.cacheName(), () -> {
            localCache.ifPresent(cache -> cache.evict(key));
            redisCache.ifPresent(cache -> cache.evict(key));
            return null;
        });
        publishEvict(cacheEvict.cacheName(), key);
        return value;
    }

    private CacheLookupResult lookup(String key, Method method) {
        Optional<CachedValue> l1 = localCache.flatMap(cache -> cache.get(key));
        if (l1.isPresent() && !l1.get().softExpired()) {
            return new CacheLookupResult(true, l1.get().unwrap());
        }

        Optional<CachedValue> l2 = redisCache.flatMap(cache -> cache.get(key, method.getGenericReturnType()));
        if (l2.isPresent() && !l2.get().softExpired()) {
            Object value = l2.get().unwrap();
            localCache.ifPresent(cache -> cache.put(
                    key,
                    value,
                    ttl(value, properties.getL1().getTtl(), properties.getL1().getNullTtl()),
                    softTtl(value, properties.getL1().getSoftTtl(), properties.getL1().getNullTtl())));
            return new CacheLookupResult(true, value);
        }

        return new CacheLookupResult(false, null);
    }

    private void instrumentedPut(String cacheName, String key, Object value) throws Throwable {
        instrumentation.record("put", cacheName, () -> {
            put(key, value);
            return null;
        });
    }

    private void publishEvict(String cacheName, String key) throws Throwable {
        instrumentation.record("publish", cacheName, () -> {
            invalidationPublisher.ifPresent(publisher -> publisher.evict(key));
            return null;
        });
    }

    private void publishEvictPrefix(String cacheName, String prefix) throws Throwable {
        instrumentation.record("publish.prefix", cacheName, () -> {
            invalidationPublisher.ifPresent(publisher -> publisher.evictPrefix(prefix));
            return null;
        });
    }

    private void put(String key, Object value) {
        localCache.ifPresent(cache -> cache.put(
                key,
                value,
                ttl(value, properties.getL1().getTtl(), properties.getL1().getNullTtl()),
                softTtl(value, properties.getL1().getSoftTtl(), properties.getL1().getNullTtl())));
        redisCache.ifPresent(cache -> cache.put(
                key,
                value,
                ttl(value, properties.getL2().getTtl(), properties.getL2().getNullTtl()),
                softTtl(value, properties.getL2().getSoftTtl(), properties.getL2().getNullTtl())));
    }

    private Duration ttl(Object value, Duration ttl, Duration nullTtl) {
        return value == null ? nullTtl : ttl;
    }

    private Duration softTtl(Object value, Duration softTtl, Duration nullTtl) {
        return value == null ? nullTtl : softTtl;
    }

    private record CacheLookupResult(boolean hit, Object value) {
    }
}
