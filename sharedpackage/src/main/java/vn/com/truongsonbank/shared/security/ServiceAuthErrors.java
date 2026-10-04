package vn.com.truongsonbank.shared.security;

import vn.com.truongsonbank.shared.exception.ErrorDescriptor;

public enum ServiceAuthErrors implements ErrorDescriptor {
    MISSING("SERVICE_TOKEN_REQUIRED", 401, "Service token is required"),
    INVALID("SERVICE_TOKEN_INVALID", 401, "Service token is invalid or expired"),
    AUDIENCE("SERVICE_AUDIENCE_INVALID", 403, "Service token audience is invalid"),
    PRINCIPAL("SERVICE_PRINCIPAL_INVALID", 403, "Service identity is invalid"),
    PERMISSION_DENIED("SERVICE_PERMISSION_DENIED", 403, "Service is not allowed to perform this operation"),
    PERMISSION_UNAVAILABLE("SERVICE_PERMISSION_UNAVAILABLE", 503, "Service permission is temporarily unavailable"),
    TOKEN_FETCH_FAILED("SERVICE_TOKEN_FETCH_FAILED", 503, "Service token could not be obtained");

    private final String code;
    private final int status;
    private final String message;

    ServiceAuthErrors(String code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    @Override public String code() { return code; }
    @Override public int httpStatus() { return status; }
    @Override public String defaultMessage() { return message; }
}
