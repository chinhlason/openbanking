package vn.com.truongsonbank.auth.authentication.infrastructure.keycloak;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import vn.com.truongsonbank.auth.authentication.config.AuthProperties;
import vn.com.truongsonbank.shared.exception.UnauthorizedException;

import java.net.URI;
import java.util.List;

@Component
public class KeycloakTokenValidator {
    private final JwtDecoder decoder;
    private final AuthProperties properties;

    KeycloakTokenValidator(AuthProperties properties) {
        this.decoder = NimbusJwtDecoder.withJwkSetUri(properties.getKeycloak().getJwksUri()).build();
        this.properties = properties;
    }

    public Jwt validate(String token) {
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
                && properties.getKeycloak().getLanIssuerUri().equals(issuer))
                || isLocalRealmIssuer(issuer);
    }

    private boolean isLocalRealmIssuer(String issuer) {
        try {
            URI uri = URI.create(issuer);
            return ("/realms/" + properties.getKeycloak().getRealm()).equals(uri.getPath()) && allowedHost(uri.getHost());
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean allowedHost(String host) {
        return host != null && (host.equals("keycloak")
                || host.equals("localhost")
                || host.equals("127.0.0.1")
                || host.startsWith("192.168.")
                || host.startsWith("10.")
                || host.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*"));
    }
}
