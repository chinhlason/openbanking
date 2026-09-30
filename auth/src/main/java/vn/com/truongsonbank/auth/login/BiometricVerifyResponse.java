package vn.com.truongsonbank.auth.login;

public record BiometricVerifyResponse(String username, String deviceId, boolean verified) {
}
