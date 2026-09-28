package vn.com.truongsonbank.shared.health;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tsb.shared.health")
public class HealthProperties {
    private boolean enabled = true;
    private Duration timeout = Duration.ofSeconds(1);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout == null ? Duration.ofSeconds(1) : timeout;
    }
}
