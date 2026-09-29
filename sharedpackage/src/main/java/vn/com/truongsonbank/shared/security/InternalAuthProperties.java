package vn.com.truongsonbank.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "tsb.shared.internal-auth")
public class InternalAuthProperties {
    private boolean enabled;
    private boolean signerEnabled;
    private boolean verifierEnabled;
    private String hmacSecret;
    private String issuer;
    private Duration maxSkew = Duration.ofSeconds(60);
    private List<String> excludedPaths = new ArrayList<>(List.of(
            "/actuator",
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-ui.html"
    ));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSignerEnabled() {
        return signerEnabled;
    }

    public void setSignerEnabled(boolean signerEnabled) {
        this.signerEnabled = signerEnabled;
    }

    public boolean isVerifierEnabled() {
        return verifierEnabled;
    }

    public void setVerifierEnabled(boolean verifierEnabled) {
        this.verifierEnabled = verifierEnabled;
    }

    public String getHmacSecret() {
        return hmacSecret;
    }

    public void setHmacSecret(String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getMaxSkew() {
        return maxSkew;
    }

    public void setMaxSkew(Duration maxSkew) {
        this.maxSkew = maxSkew;
    }

    public List<String> getExcludedPaths() {
        return excludedPaths;
    }

    public void setExcludedPaths(List<String> excludedPaths) {
        this.excludedPaths = excludedPaths == null ? new ArrayList<>() : new ArrayList<>(excludedPaths);
    }
}
