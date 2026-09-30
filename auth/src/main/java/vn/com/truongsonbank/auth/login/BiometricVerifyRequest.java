package vn.com.truongsonbank.auth.login;

public record BiometricVerifyRequest(String username, String deviceId, String challengeId, String nonce, String signature) {
}
