package vn.com.truongsonbank.shared.exception;

public class DownstreamException extends TsbException {
    public DownstreamException(Object... args) {
        super(CommonErrors.DOWNSTREAM_ERROR, args);
    }
}
