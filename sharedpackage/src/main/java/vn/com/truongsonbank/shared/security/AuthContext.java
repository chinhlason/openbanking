package vn.com.truongsonbank.shared.security;

import java.util.List;

public record AuthContext(
        String channel,
        String principalId,
        String userId,
        String customerId,
        String sessionId,
        String deviceId,
        boolean trustedDevice,
        List<String> roles,
        List<String> scopes,
        boolean dpopVerified,
        String dpopJkt,
        String dpopJti
) {
    public AuthContext {
        roles = roles == null ? List.of() : List.copyOf(roles);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }
}
