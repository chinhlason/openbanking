package vn.com.truongsonbank.shared.health;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;

class TsbSharedHealthIndicator extends AbstractHealthIndicator {
    @Override
    protected void doHealthCheck(Health.Builder builder) {
        builder.up()
                .withDetail("framework", "tsb-sharedpackage")
                .withDetail("status", "loaded");
    }
}
