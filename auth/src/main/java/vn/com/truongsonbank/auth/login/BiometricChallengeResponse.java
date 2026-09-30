package vn.com.truongsonbank.auth.login;

public record BiometricChallengeResponse(String challengeId, String nonce, long expiresInSeconds, String payload) {
}
