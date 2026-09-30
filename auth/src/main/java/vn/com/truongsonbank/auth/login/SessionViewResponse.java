package vn.com.truongsonbank.auth.login;

import java.time.Instant;

record SessionViewResponse(
        String sessionId,
        String username,
        String deviceId,
        boolean trustedDevice,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt
) {
}
