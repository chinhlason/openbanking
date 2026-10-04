package vn.com.truongsonbank.auth.authentication.domain;

import vn.com.truongsonbank.shared.exception.ErrorDescriptor;

public enum AuthErrors implements ErrorDescriptor {
    LOGIN_REQUEST_INVALID("AUTH_LOGIN_REQUEST_INVALID", "Login request is invalid"),
    LOGIN_TYPE_UNSUPPORTED("AUTH_LOGIN_TYPE_UNSUPPORTED", "Login type is not supported"),
    LOGIN_CHALLENGE_EXPIRED("AUTH_LOGIN_CHALLENGE_EXPIRED", "Login challenge is expired or already used"),
    LOGIN_CHALLENGE_MISMATCH("AUTH_LOGIN_CHALLENGE_MISMATCH", "Login challenge does not match request"),
    LOGIN_SIGNATURE_REQUIRED("AUTH_LOGIN_SIGNATURE_REQUIRED", "Login signature is required"),
    DPOP_INVALID("AUTH_DPOP_INVALID", "DPoP proof is invalid"),
    DPOP_MISMATCH("AUTH_DPOP_MISMATCH", "DPoP proof does not match login challenge"),
    KEYCLOAK_PIN_INVALID("AUTH_KEYCLOAK_PIN_INVALID", "Username or PIN is invalid"),
    KEYCLOAK_PASSWORD_INVALID("AUTH_KEYCLOAK_PASSWORD_INVALID", "Username or password is invalid"),
    KEYCLOAK_BIOMETRIC_INVALID("AUTH_KEYCLOAK_BIOMETRIC_INVALID", "Biometric credential is invalid"),
    KEYCLOAK_PASSKEY_INVALID("AUTH_KEYCLOAK_PASSKEY_INVALID", "Passkey credential is invalid"),
    KEYCLOAK_GRANT_REJECTED("AUTH_KEYCLOAK_GRANT_REJECTED", "Keycloak rejected login: {0}");

    private final String code;
    private final String defaultMessage;

    AuthErrors(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public int httpStatus() {
        return 401;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
