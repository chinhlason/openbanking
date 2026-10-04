package vn.com.truongsonbank.auth.authentication.domain;

public record LoginChallengeState(String loginType, String username, String deviceId, String nonce, String dpopJkt) {
}
