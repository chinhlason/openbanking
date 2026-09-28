package vn.com.truongsonbank.shared.exception;

public enum CommonErrors implements ErrorDescriptor {
    BAD_REQUEST("COMMON_400", 400, "Bad request"),
    VALIDATION_ERROR("COMMON_VALIDATION", 400, "Validation failed for field {0}: {1}"),
    VALIDATION_REQUIRED("COMMON_VALIDATION_REQUIRED", 400, "Please enter {0}"),
    VALIDATION_MIN("COMMON_VALIDATION_MIN", 400, "Please enter {0} with at least {1} characters"),
    VALIDATION_MAX("COMMON_VALIDATION_MAX", 400, "Please enter {0} with at most {1} characters"),
    VALIDATION_REGEX("COMMON_VALIDATION_REGEX", 400, "{0} is not in the correct format"),
    UNAUTHORIZED("COMMON_401", 401, "Unauthorized"),
    FORBIDDEN("COMMON_403", 403, "Forbidden"),
    NOT_FOUND("COMMON_404", 404, "Not found"),
    CONFLICT("COMMON_409", 409, "Conflict"),
    RATE_LIMIT("COMMON_429", 429, "Too many requests"),
    DOWNSTREAM_ERROR("COMMON_502", 502, "Downstream service error"),
    TIMEOUT("COMMON_504", 504, "Request timeout"),
    INTERNAL_ERROR("COMMON_500", 500, "Internal server error");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    CommonErrors(String code, int httpStatus, String defaultMessage) {
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
