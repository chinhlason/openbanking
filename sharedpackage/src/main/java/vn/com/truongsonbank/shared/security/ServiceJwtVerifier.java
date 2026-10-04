package vn.com.truongsonbank.shared.security;

import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import vn.com.truongsonbank.shared.exception.TsbException;

final class ServiceJwtVerifier {
    private final JwtDecoder decoder;
    private final ServiceAuthProperties properties;

    ServiceJwtVerifier(JwtDecoder decoder, ServiceAuthProperties properties) {
        this.decoder = decoder;
        this.properties = properties;
    }

    ServicePrincipal verify(String rawToken) {
        try {
            Jwt jwt = decoder.decode(rawToken);
            List<String> audiences = jwt.getAudience();
            if (properties.getExpectedAudience() != null && !properties.getExpectedAudience().isBlank()
                    && !audiences.contains(properties.getExpectedAudience())) {
                throw new TsbException(ServiceAuthErrors.AUDIENCE);
            }
            String serviceCode = first(jwt, "azp", "client_id", "sub");
            if (serviceCode == null || serviceCode.isBlank()) throw new TsbException(ServiceAuthErrors.PRINCIPAL);
            return new ServicePrincipal(serviceCode, jwt.getSubject(), audiences, jwt.getIssuer() == null ? null : jwt.getIssuer().toString(),
                    jwt.getClaimAsString("jti"), jwt.getExpiresAt() == null ? Instant.EPOCH : jwt.getExpiresAt());
        } catch (TsbException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TsbException(ServiceAuthErrors.INVALID, exception);
        }
    }

    private String first(Jwt jwt, String... names) {
        for (String name : names) {
            String value = jwt.getClaimAsString(name);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
