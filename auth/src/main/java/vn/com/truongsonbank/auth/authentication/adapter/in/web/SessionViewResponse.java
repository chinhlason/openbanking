package vn.com.truongsonbank.auth.authentication.adapter.in.web;

import java.time.Instant;

public record SessionViewResponse(
        String sessionId,
        String username,
        String deviceId,
        boolean trustedDevice,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt
) {
}
