package vn.com.truongsonbank.auth.authentication.adapter.in.web;

public record LoginVerifyRequest(
        String loginType,
        String username,
        String pin,
        String password,
        String challengeId,
        String nonce,
        String signature,
        DeviceRequest device,
        String keycloakDpopProof
) {
}
