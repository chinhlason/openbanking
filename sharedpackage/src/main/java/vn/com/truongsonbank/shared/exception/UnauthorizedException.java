package vn.com.truongsonbank.shared.exception;

public class UnauthorizedException extends TsbException {
    public UnauthorizedException(Object... args) {
        super(CommonErrors.UNAUTHORIZED, args);
    }
}
