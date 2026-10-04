package vn.com.truongsonbank.core.account.domain;

import vn.com.truongsonbank.shared.exception.ErrorDescriptor;

public enum AccountErrors implements ErrorDescriptor {
    INVALID_REQUEST("CORE_ACCOUNT_400", 400, "Account request is invalid"),
    INTERNAL_ONLY("CORE_ACCOUNT_403", 403, "Internal account operation is not allowed"),
    ACCOUNT_NOT_FOUND("CORE_ACCOUNT_404", 404, "Account not found"),
    ACCOUNT_ACCESS_DENIED("CORE_ACCOUNT_403_ACCOUNT", 403, "Account does not belong to the authenticated customer"),
    DOWNSTREAM_FAILURE("CORE_ACCOUNT_502", 502, "Core banking account operation failed");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    AccountErrors(String code, int httpStatus, String defaultMessage) {
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
