package vn.com.truongsonbank.bff;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisSessionReaderTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final RedisSessionReader reader = new RedisSessionReader(
            redis, metrics, "tsb:auth:session:");

    @Test
    void readsTheSessionObjectWrittenByAuth() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("tsb:auth:session:sid-1")).thenReturn("""
                {
                  "sessionId":"sid-1",
                  "subject":"keycloak-subject",
                  "customerId":"customer-1",
                  "username":"son",
                  "deviceId":"iphone-1",
                  "trustedDevice":true,
                  "roles":[],
                  "servicePackages":["STANDARD"],
                  "expiresAt":"2099-01-01T00:00:00Z"
                }
                """);

        RedisSessionReader.Session session = reader.read("sid-1");

        assertThat(session.customerId()).isEqualTo("customer-1");
        assertThat(session.servicePackages()).containsExactly("STANDARD");
        verify(values).get("tsb:auth:session:sid-1");
        assertThat(metrics.get("tsb.bff.session.redis.read.duration")
                .tag("outcome", "hit").timer().count()).isEqualTo(1);
    }

    @Test
    void rejectsMissingAndExpiredSessions() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("tsb:auth:session:missing")).thenReturn(null);
        when(values.get("tsb:auth:session:expired")).thenReturn("""
                {"sessionId":"expired","subject":"subject-1","expiresAt":"2020-01-01T00:00:00Z"}
                """);

        assertThatThrownBy(() -> reader.read("missing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session not found");
        assertThatThrownBy(() -> reader.read("expired"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session expired");

        verify(redis).delete("tsb:auth:session:expired");
        assertThat(metrics.get("tsb.bff.session.redis.read.duration")
                .tag("outcome", "miss").timer().count()).isEqualTo(1);
        assertThat(metrics.get("tsb.bff.session.redis.read.duration")
                .tag("outcome", "expired").timer().count()).isEqualTo(1);
    }

    @Test
    void failsClosedWhenRedisIsUnavailable() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("tsb:auth:session:sid-1")).thenThrow(new RuntimeException("Redis unavailable"));

        assertThatThrownBy(() -> reader.read("sid-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not read session from Redis");

        assertThat(metrics.get("tsb.bff.session.redis.read.duration")
                .tag("outcome", "error").timer().count()).isEqualTo(1);
    }
}
