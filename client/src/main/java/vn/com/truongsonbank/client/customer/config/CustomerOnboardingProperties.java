package vn.com.truongsonbank.client.customer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tsb.customer.onboarding")
public class CustomerOnboardingProperties {
    private String commonBaseUrl = "http://localhost:8083/common/api";
    private String authBaseUrl = "http://localhost:8084/auth/api";
    private String coreBaseUrl = "http://localhost:8085";
    private String authInternalSecret = "local-onboarding-secret";
    private String entitlementAdminKey = "local-admin-key";
    private String defaultServicePackage = "STANDARD";
    private Duration sessionTtl = Duration.ofMinutes(15);
    private Duration otpTtl = Duration.ofMinutes(5);

    public String getCommonBaseUrl() {
        return commonBaseUrl;
    }

    public void setCommonBaseUrl(String commonBaseUrl) {
        this.commonBaseUrl = commonBaseUrl;
    }

    public String getAuthBaseUrl() {
        return authBaseUrl;
    }

    public void setAuthBaseUrl(String authBaseUrl) {
        this.authBaseUrl = authBaseUrl;
    }

    public String getCoreBaseUrl() {
        return coreBaseUrl;
    }

    public void setCoreBaseUrl(String coreBaseUrl) {
        this.coreBaseUrl = coreBaseUrl;
    }

    public String getAuthInternalSecret() {
        return authInternalSecret;
    }

    public void setAuthInternalSecret(String authInternalSecret) {
        this.authInternalSecret = authInternalSecret;
    }

    public String getEntitlementAdminKey() {
        return entitlementAdminKey;
    }

    public void setEntitlementAdminKey(String entitlementAdminKey) {
        this.entitlementAdminKey = entitlementAdminKey;
    }

    public String getDefaultServicePackage() {
        return defaultServicePackage;
    }

    public void setDefaultServicePackage(String defaultServicePackage) {
        this.defaultServicePackage = defaultServicePackage;
    }

    public Duration getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(Duration sessionTtl) {
        this.sessionTtl = sessionTtl;
    }

    public Duration getOtpTtl() {
        return otpTtl;
    }

    public void setOtpTtl(Duration otpTtl) {
        this.otpTtl = otpTtl;
    }
}
