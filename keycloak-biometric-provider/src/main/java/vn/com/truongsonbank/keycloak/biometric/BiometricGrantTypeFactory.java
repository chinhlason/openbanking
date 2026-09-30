package vn.com.truongsonbank.keycloak.biometric;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.protocol.oidc.grants.OAuth2GrantType;
import org.keycloak.protocol.oidc.grants.OAuth2GrantTypeFactory;

public class BiometricGrantTypeFactory implements OAuth2GrantTypeFactory {
    static final String GRANT_TYPE = "biometric";

    @Override
    public String getId() {
        return GRANT_TYPE;
    }

    @Override
    public OAuth2GrantType create(KeycloakSession session) {
        return new BiometricGrantType();
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}
