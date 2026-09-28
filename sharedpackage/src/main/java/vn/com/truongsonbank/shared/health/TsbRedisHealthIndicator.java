package vn.com.truongsonbank.shared.health;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.data.redis.core.StringRedisTemplate;

class TsbRedisHealthIndicator extends AbstractHealthIndicator {
    private final StringRedisTemplate redis;

    TsbRedisHealthIndicator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        String pong = redis.getConnectionFactory().getConnection().ping();
        builder.up().withDetail("ping", pong);
    }
}
