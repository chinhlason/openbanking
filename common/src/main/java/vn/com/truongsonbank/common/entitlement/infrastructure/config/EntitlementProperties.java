package vn.com.truongsonbank.common.entitlement.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tsb.common.entitlement")
public class EntitlementProperties {
    private String redisChannel = "tsb:entitlement:changed";

    public String getRedisChannel() {
        return redisChannel;
    }

    public void setRedisChannel(String redisChannel) {
        this.redisChannel = redisChannel;
    }
}
