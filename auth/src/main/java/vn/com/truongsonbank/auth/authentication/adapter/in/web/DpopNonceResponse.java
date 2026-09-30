package vn.com.truongsonbank.auth.authentication.adapter.in.web;

public record DpopNonceResponse(String nonce, int expiresInSeconds) {
}
