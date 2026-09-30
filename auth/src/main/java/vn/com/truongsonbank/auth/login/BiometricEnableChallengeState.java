package vn.com.truongsonbank.auth.login;

record BiometricEnableChallengeState(String sessionId, String username, String deviceId, String nonce, String dpopJkt) {
}
