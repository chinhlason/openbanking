package vn.com.truongsonbank.shared.security;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

class RedisInternalAuthNonceStore implements InternalAuthNonceStore {
    private final StringRedisTemplate redis;

    RedisInternalAuthNonceStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean markIfNew(String issuer, String nonce, Duration ttl) {
        Boolean stored = redis.opsForValue().setIfAbsent(key(issuer, nonce), "1", ttl);
        return Boolean.TRUE.equals(stored);
    }

    private String key(String issuer, String nonce) {
        return "tsb:internal-auth:nonce:" + issuer + ":" + nonce;
    }
}
