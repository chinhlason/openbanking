package vn.com.truongsonbank.shared.exception;

public class TimeoutException extends TsbException {
    public TimeoutException(Object... args) {
        super(CommonErrors.TIMEOUT, args);
    }
}
