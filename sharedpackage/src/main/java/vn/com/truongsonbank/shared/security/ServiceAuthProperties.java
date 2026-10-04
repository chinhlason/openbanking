package vn.com.truongsonbank.shared.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tsb.shared.security.service-auth")
public class ServiceAuthProperties {
    private boolean enabled;
    private String serviceCode;
    private String issuerUri;
    private String jwkSetUri;
    private String expectedAudience;
    private String clientId;
    private String clientSecret;
    private String tokenUri;
    private String audience;
    private List<String> excludedPaths = new ArrayList<>(List.of("/actuator", "/v3/api-docs", "/swagger-ui", "/swagger-ui.html"));
    private PermissionResolver permissionResolver = new PermissionResolver();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
    public String getIssuerUri() { return issuerUri; }
    public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }
    public String getJwkSetUri() { return jwkSetUri; }
    public void setJwkSetUri(String jwkSetUri) { this.jwkSetUri = jwkSetUri; }
    public String getExpectedAudience() { return expectedAudience; }
    public void setExpectedAudience(String expectedAudience) { this.expectedAudience = expectedAudience; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getTokenUri() { return tokenUri; }
    public void setTokenUri(String tokenUri) { this.tokenUri = tokenUri; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public List<String> getExcludedPaths() { return excludedPaths; }
    public void setExcludedPaths(List<String> excludedPaths) { this.excludedPaths = excludedPaths == null ? new ArrayList<>() : new ArrayList<>(excludedPaths); }
    public PermissionResolver getPermissionResolver() { return permissionResolver; }
    public void setPermissionResolver(PermissionResolver permissionResolver) { this.permissionResolver = permissionResolver == null ? new PermissionResolver() : permissionResolver; }

    public static class PermissionResolver {
        private boolean enabled;
        private String baseUrl = "http://localhost:8083/common/api";
        private String path = "/entitlements/internal/subjects/{subjectType}/{subjectCode}/operations";
        private Duration cacheTtl = Duration.ofSeconds(60);
        private String redisChannel = "tsb:entitlement:changed";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public Duration getCacheTtl() { return cacheTtl; }
        public void setCacheTtl(Duration cacheTtl) { this.cacheTtl = cacheTtl; }
        public String getRedisChannel() { return redisChannel; }
        public void setRedisChannel(String redisChannel) { this.redisChannel = redisChannel; }
    }
}
