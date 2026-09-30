package vn.com.truongsonbank.auth.authentication.adapter.in.web;

public record BiometricVerifyResponse(String username, String deviceId, boolean verified) {
}
