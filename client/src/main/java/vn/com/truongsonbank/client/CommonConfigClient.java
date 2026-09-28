package vn.com.truongsonbank.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
class CommonConfigClient implements MessageListener {
    private final CommonConfigProperties properties;
    private final RedisConnectionFactory redisConnectionFactory;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AtomicReference<CommonConfigSnapshot> snapshot = new AtomicReference<>();
    private ThreadPoolTaskScheduler scheduler;
    private ScheduledFuture<?> pollingTask;
    private RedisConnection subscription;

    CommonConfigClient(CommonConfigProperties properties,
                       RedisConnectionFactory redisConnectionFactory,
                       ObjectMapper objectMapper) {
        this.properties = properties;
        this.redisConnectionFactory = redisConnectionFactory;
        this.restClient = RestClient.builder().baseUrl(properties.getServerUrl()).build();
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void start() {
        if (!properties.isEnabled()) {
            return;
        }
        reload("startup", true);
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("common-config-");
        scheduler.initialize();
        pollingTask = scheduler.scheduleAtFixedRate(() -> reload("polling", false), properties.getPollingInterval());
        if (properties.getRedis().isEnabled()) {
            scheduler.execute(this::subscribe);
        }
    }

    @PreDestroy
    void stop() {
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

    String getString(String key, String defaultValue) {
        CommonConfigSnapshot current = snapshot.get();
        if (current == null || current.flat() == null) {
            return defaultValue;
        }
        return current.flat().getOrDefault(key, defaultValue);
    }

    boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(getString(key, String.valueOf(defaultValue)));
    }

    long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(getString(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    CommonConfigSnapshot snapshot() {
        return snapshot.get();
    }

    void reload(String source, boolean failFast) {
        try {
            String body = restClient.get()
                    .uri("/config/v1/apps/{app}/profiles/{profile}", properties.getApp(), properties.getProfile())
                    .header("X-Config-Api-Key", properties.getApiKey())
                    .retrieve()
                    .body(String.class);
            CommonConfigSnapshot next = readSnapshot(body);
            if (next == null || next.flat() == null) {
                throw new IllegalStateException("empty config snapshot");
            }
            CommonConfigSnapshot previous = snapshot.getAndSet(next);
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

    private CommonConfigSnapshot readSnapshot(String body) throws java.io.IOException {
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
        com.fasterxml.jackson.databind.JsonNode payload = root.has("data") ? root.get("data") : root;
        return objectMapper.treeToValue(payload, CommonConfigSnapshot.class);
    }

    private void subscribe() {
        try {
            subscription = redisConnectionFactory.getConnection();
            subscription.subscribe(this, properties.getRedis().getChannel().getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
}

@Configuration
@EnableConfigurationProperties(CommonConfigProperties.class)
class CommonConfigClientConfiguration {
    @Bean
    @ConditionalOnMissingBean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
