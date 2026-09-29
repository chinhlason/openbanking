package vn.com.truongsonbank.shared.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;

class LocalInternalAuthNonceStore implements InternalAuthNonceStore {
    private final Cache<String, Boolean> cache;

    LocalInternalAuthNonceStore(Duration ttl) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterWrite(ttl)
                .build();
    }

    @Override
    public boolean markIfNew(String issuer, String nonce, Duration ttl) {
        String key = issuer + ":" + nonce;
        Boolean previous = cache.asMap().putIfAbsent(key, Boolean.TRUE);
        return previous == null;
    }
}
