package vn.com.truongsonbank.auth.authentication.adapter.in.web;

public record BiometricVerifyRequest(String username, String deviceId, String challengeId, String nonce, String signature) {
}
