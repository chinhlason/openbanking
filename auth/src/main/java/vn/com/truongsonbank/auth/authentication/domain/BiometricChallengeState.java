package vn.com.truongsonbank.auth.authentication.domain;

public record BiometricChallengeState(String username, String deviceId, String nonce) {
}
