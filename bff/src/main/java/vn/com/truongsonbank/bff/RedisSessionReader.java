package vn.com.truongsonbank.bff;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
class RedisSessionReader {
    private static final String METRIC = "tsb.bff.session.redis.read.duration";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final String keyPrefix;

    RedisSessionReader(StringRedisTemplate redis,
                       MeterRegistry meterRegistry,
                       @Value("${tsb.bff.session.redis-key-prefix:tsb:auth:session:}") String keyPrefix) {
        this.redis = redis;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.meterRegistry = meterRegistry;
        this.keyPrefix = keyPrefix;
    }

    Session read(String sessionId) {
        long startedAt = System.nanoTime();
        String outcome = "error";
        try {
            String json = redis.opsForValue().get(keyPrefix + sessionId);
            if (json == null) {
                outcome = "miss";
                throw new IllegalStateException("Session not found");
            }
            Session session;
            try {
                session = objectMapper.readValue(json, Session.class);
            } catch (JsonProcessingException ex) {
                outcome = "invalid";
                throw new IllegalStateException("Invalid session object", ex);
            }
            if (!sessionId.equals(session.sessionId()) || session.subject() == null
                    || session.subject().isBlank() || session.expiresAt() == null) {
                outcome = "invalid";
                throw new IllegalStateException("Invalid session object");
            }
            if (!session.expiresAt().isAfter(Instant.now())) {
                outcome = "expired";
                try {
                    redis.delete(keyPrefix + sessionId);
                } catch (RuntimeException ignored) {
                    // The session is rejected even when best-effort cleanup fails.
                }
                throw new IllegalStateException("Session expired");
            }
            outcome = "hit";
            return session;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Could not read session from Redis", ex);
        } finally {
            meterRegistry.timer(METRIC, "outcome", outcome)
                    .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Session(String sessionId,
                   String subject,
                   String customerId,
                   String username,
                   String deviceId,
                   boolean trustedDevice,
                   List<String> roles,
                   List<String> servicePackages,
                   Instant expiresAt) {
        Session {
            roles = roles == null ? List.of() : List.copyOf(roles);
            servicePackages = servicePackages == null ? List.of() : List.copyOf(servicePackages);
        }
    }
}
