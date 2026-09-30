package vn.com.truongsonbank.auth.authentication.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tsb.auth")
public class AuthProperties {
    private Duration sessionTtl = Duration.ofMinutes(10);
    private String biometricGrantSecret = "local-biometric-grant-secret";
    private String onboardingInternalSecret = "local-onboarding-secret";
    private final Keycloak keycloak = new Keycloak();

    public Duration getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(Duration sessionTtl) {
        this.sessionTtl = sessionTtl;
    }

    public String getBiometricGrantSecret() {
        return biometricGrantSecret;
    }

    public void setBiometricGrantSecret(String biometricGrantSecret) {
        this.biometricGrantSecret = biometricGrantSecret;
    }

    public String getOnboardingInternalSecret() {
        return onboardingInternalSecret;
    }

    public void setOnboardingInternalSecret(String onboardingInternalSecret) {
        this.onboardingInternalSecret = onboardingInternalSecret;
    }

    public Keycloak getKeycloak() {
        return keycloak;
    }

    public static class Keycloak {
        private String tokenUri;
        private String issuerUri;
        private String internalIssuerUri;
        private String lanIssuerUri;
        private String jwksUri;
        private String mobileClientId;
        private String adminBaseUrl = "http://localhost:8088";
        private String adminRealm = "master";
        private String realm = "truongsonbank";
        private String adminClientId = "admin-cli";
        private String adminUsername = "admin";
        private String adminPassword = "admin";

        public String getTokenUri() {
            return tokenUri;
        }

        public void setTokenUri(String tokenUri) {
            this.tokenUri = tokenUri;
        }

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri;
        }

        public String getInternalIssuerUri() {
            return internalIssuerUri;
        }

        public void setInternalIssuerUri(String internalIssuerUri) {
            this.internalIssuerUri = internalIssuerUri;
        }

        public String getLanIssuerUri() {
            return lanIssuerUri;
        }

        public void setLanIssuerUri(String lanIssuerUri) {
            this.lanIssuerUri = lanIssuerUri;
        }

        public String getJwksUri() {
            return jwksUri;
        }

        public void setJwksUri(String jwksUri) {
            this.jwksUri = jwksUri;
        }

        public String getMobileClientId() {
            return mobileClientId;
        }

        public void setMobileClientId(String mobileClientId) {
            this.mobileClientId = mobileClientId;
        }

        public String getAdminBaseUrl() {
            return adminBaseUrl;
        }

        public void setAdminBaseUrl(String adminBaseUrl) {
            this.adminBaseUrl = adminBaseUrl;
        }

        public String getAdminRealm() {
            return adminRealm;
        }

        public void setAdminRealm(String adminRealm) {
            this.adminRealm = adminRealm;
        }

        public String getRealm() {
            return realm;
        }

        public void setRealm(String realm) {
            this.realm = realm;
        }

        public String getAdminClientId() {
            return adminClientId;
        }

        public void setAdminClientId(String adminClientId) {
            this.adminClientId = adminClientId;
        }

        public String getAdminUsername() {
            return adminUsername;
        }

        public void setAdminUsername(String adminUsername) {
            this.adminUsername = adminUsername;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public void setAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
        }
    }
}
