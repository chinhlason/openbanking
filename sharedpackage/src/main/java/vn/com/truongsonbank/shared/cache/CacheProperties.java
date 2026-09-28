package vn.com.truongsonbank.shared.cache;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tsb.shared.cache")
public class CacheProperties {
    private boolean enabled = true;
    private String keyPrefix = "tsb";
    private final L1 l1 = new L1();
    private final L2 l2 = new L2();
    private final Invalidation invalidation = new Invalidation();
    private final Tracking tracking = new Tracking();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public L1 getL1() {
        return l1;
    }

    public L2 getL2() {
        return l2;
    }

    public Invalidation getInvalidation() {
        return invalidation;
    }

    public Tracking getTracking() {
        return tracking;
    }

    public static class L1 {
        private boolean enabled = true;
        private long maximumSize = 10_000;
        private Duration ttl = Duration.ofMinutes(10);
        private Duration softTtl = Duration.ofMinutes(5);
        private Duration nullTtl = Duration.ofSeconds(30);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public Duration getSoftTtl() {
            return softTtl;
        }

        public void setSoftTtl(Duration softTtl) {
            this.softTtl = softTtl;
        }

        public Duration getNullTtl() {
            return nullTtl;
        }

        public void setNullTtl(Duration nullTtl) {
            this.nullTtl = nullTtl;
        }
    }

    public static class L2 {
        private boolean enabled;
        private Duration ttl = Duration.ofMinutes(30);
        private Duration softTtl = Duration.ofMinutes(10);
        private Duration nullTtl = Duration.ofSeconds(30);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public Duration getSoftTtl() {
            return softTtl;
        }

        public void setSoftTtl(Duration softTtl) {
            this.softTtl = softTtl;
        }

        public Duration getNullTtl() {
            return nullTtl;
        }

        public void setNullTtl(Duration nullTtl) {
            this.nullTtl = nullTtl;
        }
    }

    public static class Invalidation {
        private boolean enabled = true;
        private String channel = "tsb-cache-invalidation";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }
    }

    public static class Tracking {
        private boolean enabled;
        private boolean configureNotifyKeyspaceEvents;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isConfigureNotifyKeyspaceEvents() {
            return configureNotifyKeyspaceEvents;
        }

        public void setConfigureNotifyKeyspaceEvents(boolean configureNotifyKeyspaceEvents) {
            this.configureNotifyKeyspaceEvents = configureNotifyKeyspaceEvents;
        }
    }
}
