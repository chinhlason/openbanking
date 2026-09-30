package vn.com.truongsonbank.auth.authentication.domain;

public record BiometricEnableChallengeState(String sessionId, String username, String deviceId, String nonce, String dpopJkt) {
}
