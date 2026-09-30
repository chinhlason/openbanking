package vn.com.truongsonbank.auth.authentication.adapter.in.web;

public record BiometricChallengeResponse(String challengeId, String nonce, long expiresInSeconds, String payload) {
}
