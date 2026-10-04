package vn.com.truongsonbank.keycloak.biometric;

public class PasskeyGrantType extends BiometricGrantType {
    @Override
    protected String authMethod() {
        return "passkey";
    }

    @Override
    protected String credentialType() {
        return BiometricCredentialResource.PASSKEY_TYPE;
    }

    @Override
    protected String invalidCredentialMessage() {
        return "Invalid passkey credential";
    }
}
