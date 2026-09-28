package vn.com.truongsonbank.shared.exception;

public class TsbException extends RuntimeException {
    private final ErrorDescriptor error;
    private final Object[] args;

    public TsbException(ErrorDescriptor error, Object... args) {
        super(error.defaultMessage());
        this.error = error;
        this.args = args == null ? new Object[0] : args.clone();
    }

    public TsbException(ErrorDescriptor error, Throwable cause, Object... args) {
        super(error.defaultMessage(), cause);
        this.error = error;
        this.args = args == null ? new Object[0] : args.clone();
    }

    public ErrorDescriptor error() {
        return error;
    }

    public Object[] args() {
        return args.clone();
    }
}
