package vn.com.truongsonbank.shared.security;

import org.springframework.core.env.Environment;
import vn.com.truongsonbank.shared.config.TsbCommonConfigClient;

import java.time.Duration;

public class InternalAuthSecretProvider {
    static final String SECRET_KEY = "security.internal-auth.hmac-secret";
    static final String ISSUER_KEY = "security.internal-auth.issuer";
    static final String MAX_SKEW_SECONDS_KEY = "security.internal-auth.max-skew-seconds";

    private final InternalAuthProperties properties;
    private final TsbCommonConfigClient commonConfigClient;
    private final Environment environment;

    InternalAuthSecretProvider(
            InternalAuthProperties properties,
            TsbCommonConfigClient commonConfigClient,
            Environment environment) {
        this.properties = properties;
        this.commonConfigClient = commonConfigClient;
        this.environment = environment;
        if (properties.isEnabled() && blank(secret())) {
            throw new IllegalStateException("tsb.shared.internal-auth.hmac-secret is required");
        }
    }

    public String secret() {
        String fallback = properties.getHmacSecret();
        if (commonConfigClient == null) {
            return fallback;
        }
        return commonConfigClient.getString(SECRET_KEY, fallback);
    }

    public String issuer() {
        String fallback = properties.getIssuer();
        if (blank(fallback)) {
            fallback = environment.getProperty("spring.application.name", "app");
        }
        if (commonConfigClient == null) {
            return fallback;
        }
        return commonConfigClient.getString(ISSUER_KEY, fallback);
    }

    public Duration maxSkew() {
        if (commonConfigClient == null) {
            return properties.getMaxSkew();
        }
        long fallback = properties.getMaxSkew().toSeconds();
        return Duration.ofSeconds(commonConfigClient.getLong(MAX_SKEW_SECONDS_KEY, fallback));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
