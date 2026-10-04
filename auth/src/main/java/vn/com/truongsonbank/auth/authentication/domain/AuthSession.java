package vn.com.truongsonbank.auth.authentication.domain;

import java.time.Instant;
import java.util.List;

public record AuthSession(
        String sessionId,
        String subject,
        String customerId,
        String username,
        String deviceId,
        String dpopJkt,
        boolean trustedDevice,
        List<String> roles,
        List<String> servicePackages,
        Instant createdAt,
        Instant expiresAt
) {
}
