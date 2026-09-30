package vn.com.truongsonbank.auth.login;

public record DpopNonceResponse(String nonce, int expiresInSeconds) {
}
