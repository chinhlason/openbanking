package vn.com.truongsonbank.common.config.adapter.outbound.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import vn.com.truongsonbank.common.config.domain.model.ConfigPublishEvent;
import vn.com.truongsonbank.common.config.domain.port.ConfigEventPublisher;
import vn.com.truongsonbank.common.config.infrastructure.config.ConfigServerProperties;

@Component
public class RedisConfigEventPublisher implements ConfigEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(RedisConfigEventPublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final ConfigServerProperties properties;

    public RedisConfigEventPublisher(StringRedisTemplate redisTemplate,
                                     ConfigServerProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void publish(ConfigPublishEvent event) {
        try {
            redisTemplate.convertAndSend(properties.getRedisChannel(), json(event));
        } catch (Exception ex) {
            log.warn("Could not publish config event app={} profile={} version={}: {}",
                    event.app(), event.profile(), event.version(), ex.getMessage());
        }
    }

    private String json(ConfigPublishEvent event) {
        String keys = event.changedKeys().stream()
                .map(key -> "\"" + escape(key) + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return """
                {"app":"%s","profile":"%s","version":%d,"changedKeys":[%s],"publishedAt":"%s"}
                """.formatted(escape(event.app()), escape(event.profile()), event.version(), keys, event.publishedAt());
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
