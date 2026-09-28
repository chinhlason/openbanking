package vn.com.truongsonbank.shared.exception;

public class RateLimitException extends TsbException {
    public RateLimitException(Object... args) {
        super(CommonErrors.RATE_LIMIT, args);
    }
}
