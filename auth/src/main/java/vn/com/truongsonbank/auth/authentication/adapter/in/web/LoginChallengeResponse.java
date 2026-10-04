package vn.com.truongsonbank.auth.authentication.adapter.in.web;

import java.util.List;

public record LoginChallengeResponse(
        String challengeId,
        String nonce,
        long expiresInSeconds,
        String payload,
        List<String> availableMethods,
        boolean trustedDeviceRequired
) {
}
