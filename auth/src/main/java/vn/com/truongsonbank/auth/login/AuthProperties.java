package vn.com.truongsonbank.auth.login;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tsb.auth")
public class AuthProperties {
    private Duration sessionTtl = Duration.ofMinutes(10);
    private String biometricGrantSecret = "local-biometric-grant-secret";
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
    }
}
