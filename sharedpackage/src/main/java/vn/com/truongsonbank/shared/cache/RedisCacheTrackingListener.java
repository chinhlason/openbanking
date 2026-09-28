package vn.com.truongsonbank.shared.cache;

import java.nio.charset.StandardCharsets;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

class RedisCacheTrackingListener implements MessageListener {
    private final LocalCacheStore localCache;
    private final CacheProperties properties;

    RedisCacheTrackingListener(LocalCacheStore localCache, CacheProperties properties) {
        this.localCache = localCache;
        this.properties = properties;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = new String(message.getBody(), StandardCharsets.UTF_8);
        String prefix = properties.getKeyPrefix() + ":";
        if (key.startsWith(prefix)) {
            localCache.evict(key);
        }
    }
}
