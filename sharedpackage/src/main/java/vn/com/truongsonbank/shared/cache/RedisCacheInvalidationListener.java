package vn.com.truongsonbank.shared.cache;

import java.nio.charset.StandardCharsets;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

class RedisCacheInvalidationListener implements MessageListener {
    private final LocalCacheStore localCache;

    RedisCacheInvalidationListener(LocalCacheStore localCache) {
        this.localCache = localCache;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        CacheInvalidationEvent event = CacheInvalidationEvent.decode(
                new String(message.getBody(), StandardCharsets.UTF_8));
        if (event == null) {
            return;
        }
        if (event.prefix()) {
            localCache.evictPrefix(event.key());
            return;
        }
        localCache.evict(event.key());
    }
}
