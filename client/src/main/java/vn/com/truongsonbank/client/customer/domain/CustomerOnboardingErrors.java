package vn.com.truongsonbank.client.customer.domain;

import vn.com.truongsonbank.shared.exception.ErrorDescriptor;

public enum CustomerOnboardingErrors implements ErrorDescriptor {
    INVALID_PHONE("CUSTOMER_ONBOARDING_PHONE_INVALID", 400, "Số điện thoại không hợp lệ"),
    INVALID_OTP("CUSTOMER_ONBOARDING_OTP_INVALID", 400, "Mã OTP không hợp lệ"),
    EXPIRED("CUSTOMER_ONBOARDING_EXPIRED", 400, "Phiên đăng ký đã hết hạn"),
    INVALID_STATE("CUSTOMER_ONBOARDING_INVALID_STATE", 400, "Bước đăng ký không hợp lệ"),
    PIN_WEAK("CUSTOMER_ONBOARDING_PIN_WEAK", 400, "PIN phải gồm 6 chữ số"),
    DUPLICATE_CUSTOMER("CUSTOMER_DUPLICATE", 409, "Khách hàng đã tồn tại");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    CustomerOnboardingErrors(String code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
