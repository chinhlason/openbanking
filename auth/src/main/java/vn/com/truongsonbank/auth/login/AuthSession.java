package vn.com.truongsonbank.auth.login;

import java.time.Instant;
import java.util.List;

public record AuthSession(
        String sessionId,
        String subject,
        String username,
        String deviceId,
        String dpopJkt,
        boolean trustedDevice,
        List<String> roles,
        Instant createdAt,
        Instant expiresAt
) {
}
