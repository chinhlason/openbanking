package vn.com.truongsonbank.shared.exception;

public class ForbiddenException extends TsbException {
    public ForbiddenException(Object... args) {
        super(CommonErrors.FORBIDDEN, args);
    }
}
