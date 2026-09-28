package vn.com.truongsonbank.shared.exception;

public class NotFoundException extends TsbException {
    public NotFoundException(Object... args) {
        super(CommonErrors.NOT_FOUND, args);
    }
}
