package vn.com.truongsonbank.keycloak.biometric;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

public class BiometricCredentialResourceProvider implements RealmResourceProvider {
    private final KeycloakSession session;

    BiometricCredentialResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new BiometricCredentialResource(session);
    }

    @Override
    public void close() {
    }
}
