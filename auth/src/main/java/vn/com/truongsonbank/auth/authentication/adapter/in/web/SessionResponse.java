package vn.com.truongsonbank.auth.authentication.adapter.in.web;

import java.time.Instant;
import java.util.List;

public record SessionResponse(
        String sessionId,
        long expiresInSeconds,
        Instant expiresAt,
        String subject,
        String username,
        String deviceId,
        boolean trustedDevice,
        List<String> roles
) {
}
