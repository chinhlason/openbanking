package vn.com.truongsonbank.shared.exception;

public interface ErrorDescriptor {
    String code();

    int httpStatus();

    String defaultMessage();
}
