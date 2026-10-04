package vn.com.truongsonbank.keycloak.biometric;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.protocol.oidc.grants.OAuth2GrantType;
import org.keycloak.protocol.oidc.grants.OAuth2GrantTypeFactory;

public class TsbPinGrantTypeFactory implements OAuth2GrantTypeFactory {
    @Override
    public String getId() {
        return "tsb-pin";
    }

    @Override
    public OAuth2GrantType create(KeycloakSession session) {
        return new TsbPinGrantType();
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
