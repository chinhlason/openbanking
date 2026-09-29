package vn.com.truongsonbank.shared.security;

import vn.com.truongsonbank.shared.exception.ErrorDescriptor;

public enum InternalAuthErrors implements ErrorDescriptor {
    MISSING("INTERNAL_AUTH_MISSING", 401, "Internal auth header is missing"),
    INVALID_SIGNATURE("INTERNAL_AUTH_INVALID_SIGNATURE", 401, "Internal auth signature is invalid"),
    REPLAY("INTERNAL_AUTH_REPLAY", 401, "Internal auth nonce replay detected"),
    FORBIDDEN("INTERNAL_AUTH_FORBIDDEN", 403, "Forbidden");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    InternalAuthErrors(String code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
