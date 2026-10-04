package vn.com.truongsonbank.common.entitlement.domain;

import vn.com.truongsonbank.shared.exception.ErrorDescriptor;

public enum EntitlementErrors implements ErrorDescriptor {
    NOT_FOUND("ENTITLEMENT_NOT_FOUND", 404, "Entitlement snapshot not found"),
    REQUEST_INVALID("ENTITLEMENT_REQUEST_INVALID", 400, "Invalid entitlement snapshot"),
    VERSION_STALE("ENTITLEMENT_VERSION_STALE", 409, "Entitlement version must increase"),
    DUPLICATE_CODE("ENTITLEMENT_DUPLICATE_CODE", 409, "An entitlement code already exists"),
    INVALID_ADMIN_KEY("ENTITLEMENT_INVALID_ADMIN_KEY", 401, "Invalid entitlement admin key");

    private final String code;
    private final int httpStatus;
    private final String message;

    EntitlementErrors(String code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public String code() { return code; }
    public int httpStatus() { return httpStatus; }
    public String defaultMessage() { return message; }
}
