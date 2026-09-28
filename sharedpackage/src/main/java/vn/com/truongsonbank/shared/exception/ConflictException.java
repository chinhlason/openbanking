package vn.com.truongsonbank.shared.exception;

public class ConflictException extends TsbException {
    public ConflictException(Object... args) {
        super(CommonErrors.CONFLICT, args);
    }
}
