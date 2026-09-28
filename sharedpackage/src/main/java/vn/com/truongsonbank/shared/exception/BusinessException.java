package vn.com.truongsonbank.shared.exception;

public class BusinessException extends TsbException {
    public BusinessException(ErrorDescriptor error, Object... args) {
        super(error, args);
    }
}
