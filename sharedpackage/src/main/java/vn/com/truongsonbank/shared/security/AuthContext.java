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
        List<String> entitlements,
        long entitlementVersion,
        boolean dpopVerified,
        String dpopJkt,
        String dpopJti
) {
    public AuthContext {
        roles = roles == null ? List.of() : List.copyOf(roles);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        entitlements = entitlements == null ? List.of() : List.copyOf(entitlements);
    }

    public AuthContext(String channel, String principalId, String userId, String customerId,
                       String sessionId, String deviceId, boolean trustedDevice,
                       List<String> roles, List<String> scopes, boolean dpopVerified,
                       String dpopJkt, String dpopJti) {
        this(channel, principalId, userId, customerId, sessionId, deviceId, trustedDevice,
                roles, scopes, List.of(), dpopVerified, dpopJkt, dpopJti);
    }

    public AuthContext(String channel, String principalId, String userId, String customerId,
                       String sessionId, String deviceId, boolean trustedDevice,
                       List<String> roles, List<String> scopes, List<String> entitlements,
                       boolean dpopVerified, String dpopJkt, String dpopJti) {
        this(channel, principalId, userId, customerId, sessionId, deviceId, trustedDevice,
                roles, scopes, entitlements, 0L, dpopVerified, dpopJkt, dpopJti);
    }
}
