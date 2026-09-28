package vn.com.truongsonbank.shared.exception;

public class ValidationException extends TsbException {
    public ValidationException(ErrorDescriptor error, Object... args) {
        super(error, args);
    }

    public ValidationException(Object... args) {
        super(CommonErrors.VALIDATION_ERROR, args);
    }
}
