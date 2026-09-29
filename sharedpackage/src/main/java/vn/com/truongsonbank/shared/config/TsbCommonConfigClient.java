package vn.com.truongsonbank.shared.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

public class TsbCommonConfigClient implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(TsbCommonConfigClient.class);

    private final TsbCommonConfigProperties properties;
    private final RedisConnectionFactory redisConnectionFactory;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final AtomicReference<TsbCommonConfigSnapshot> snapshot = new AtomicReference<>();
    private ThreadPoolTaskScheduler scheduler;
    private ScheduledFuture<?> pollingTask;
    private RedisConnection subscription;

    public TsbCommonConfigClient(
            TsbCommonConfigProperties properties,
            RedisConnectionFactory redisConnectionFactory,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.redisConnectionFactory = redisConnectionFactory;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).build();
    }

    @PostConstruct
    public void start() {
        reload("startup", properties.isFailFast());
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("tsb-common-config-");
        scheduler.initialize();
        pollingTask = scheduler.scheduleAtFixedRate(() -> reload("polling", false), properties.getPollingInterval());
        if (properties.getRedis().isEnabled() && redisConnectionFactory != null) {
            scheduler.execute(this::subscribe);
        }
    }

    @PreDestroy
    public void stop() {
        if (pollingTask != null) {
            pollingTask.cancel(true);
        }
        if (subscription != null) {
            subscription.close();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    public String getString(String key, String defaultValue) {
        TsbCommonConfigSnapshot current = snapshot.get();
        if (current == null || current.flat() == null) {
            return defaultValue;
        }
        return current.flat().getOrDefault(key, defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(getString(key, String.valueOf(defaultValue)));
    }

    public long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(getString(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public TsbCommonConfigSnapshot snapshot() {
        return snapshot.get();
    }

    public void reload(String source, boolean failFast) {
        try {
            String body = restClient.get()
                    .uri("/config/v1/apps/{app}/profiles/{profile}", required(properties.getApp(), "app"), required(properties.getProfile(), "profile"))
                    .header("X-Config-Api-Key", required(properties.getApiKey(), "api-key"))
                    .retrieve()
                    .body(String.class);
            TsbCommonConfigSnapshot next = readSnapshot(body);
            if (next == null || next.flat() == null) {
                throw new IllegalStateException("empty config snapshot");
            }
            TsbCommonConfigSnapshot previous = snapshot.getAndSet(next);
            if (previous == null || previous.version() != next.version()) {
                log.info("Loaded common config app={} profile={} version={} source={}",
                        next.app(), next.profile(), next.version(), source);
            }
        } catch (Exception ex) {
            if (failFast) {
                throw new IllegalStateException("Could not load startup common config", ex);
            }
            log.warn("Common config reload failed source={}: {}", source, ex.getMessage());
        }
    }

    private TsbCommonConfigSnapshot readSnapshot(String body) throws java.io.IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode payload = root.has("data") ? root.get("data") : root;
        return objectMapper.treeToValue(payload, TsbCommonConfigSnapshot.class);
    }

    private void subscribe() {
        try {
            subscription = redisConnectionFactory.getConnection();
            subscription.subscribe(this, properties.getRedis().getChannel().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.warn("Common config redis subscription stopped: {}", ex.getMessage());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            Map<?, ?> event = objectMapper.readValue(message.getBody(), Map.class);
            if (properties.getApp().equals(event.get("app")) && properties.getProfile().equals(event.get("profile"))) {
                reload("pubsub", false);
            }
        } catch (Exception ex) {
            log.warn("Invalid common config event: {}", ex.getMessage());
        }
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("tsb.shared.common-config." + name + " is required");
        }
        return value;
    }
}
