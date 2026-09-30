package vn.com.truongsonbank.auth.login;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import vn.com.truongsonbank.shared.exception.UnauthorizedException;

import java.util.List;

@Component
class KeycloakTokenValidator {
    private final JwtDecoder decoder;
    private final AuthProperties properties;

    KeycloakTokenValidator(AuthProperties properties) {
        this.decoder = NimbusJwtDecoder.withJwkSetUri(properties.getKeycloak().getJwksUri()).build();
        this.properties = properties;
    }

    Jwt validate(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            String issuer = jwt.getIssuer() == null ? "" : jwt.getIssuer().toString();
            String clientId = properties.getKeycloak().getMobileClientId();
            List<String> audience = jwt.getAudience();
            String azp = jwt.getClaimAsString("azp");

            if (!isAcceptedIssuer(issuer)) {
                throw new UnauthorizedException();
            }
            if (!clientId.equals(azp) && !audience.contains(clientId)) {
                throw new UnauthorizedException();
            }
            return jwt;
        } catch (RuntimeException ex) {
            throw new UnauthorizedException();
        }
    }

    private boolean isAcceptedIssuer(String issuer) {
        return properties.getKeycloak().getIssuerUri().equals(issuer)
                || (properties.getKeycloak().getInternalIssuerUri() != null
                && properties.getKeycloak().getInternalIssuerUri().equals(issuer))
                || (properties.getKeycloak().getLanIssuerUri() != null
                && properties.getKeycloak().getLanIssuerUri().equals(issuer));
    }
}
