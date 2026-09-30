package vn.com.truongsonbank.auth.login;

public record BiometricEnableRequest(String publicKey, String challengeId, String nonce, String signature) {
}
