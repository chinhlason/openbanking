package vn.com.truongsonbank.client.customer.domain;

public enum CustomerOnboardingStatus {
    STARTED,
    OTP_VERIFIED,
    QR_VERIFIED,
    NFC_VERIFIED,
    LIVENESS_VERIFIED,
    AUTH_CREATED,
    ACCOUNT_CREATED,
    COMPLETED
}
