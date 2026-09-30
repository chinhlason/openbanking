package vn.com.truongsonbank.auth.authentication.adapter.in.web;

public record BiometricEnableRequest(String publicKey, String challengeId, String nonce, String signature) {
}
