package vn.com.truongsonbank.common.config.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "tsb.common.config")
public class ConfigServerProperties {
    private String adminApiKey = "local-admin-key";
    private String redisChannel = "tsb:config:changed";
    private Map<String, String> bootstrapClients = new LinkedHashMap<>();

    public String getAdminApiKey() {
        return adminApiKey;
    }

    public void setAdminApiKey(String adminApiKey) {
        this.adminApiKey = adminApiKey;
    }

    public String getRedisChannel() {
        return redisChannel;
    }

    public void setRedisChannel(String redisChannel) {
        this.redisChannel = redisChannel;
    }

    public Map<String, String> getBootstrapClients() {
        return bootstrapClients;
    }

    public void setBootstrapClients(Map<String, String> bootstrapClients) {
        this.bootstrapClients = bootstrapClients == null ? new LinkedHashMap<>() : bootstrapClients;
    }
}
