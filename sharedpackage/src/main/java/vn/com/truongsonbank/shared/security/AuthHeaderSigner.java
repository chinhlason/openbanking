package vn.com.truongsonbank.shared.security;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class AuthHeaderSigner {
    private final InternalAuthSecretProvider secretProvider;
    private final Clock clock;

    public AuthHeaderSigner(InternalAuthSecretProvider secretProvider) {
        this(secretProvider, Clock.systemUTC());
    }

    AuthHeaderSigner(InternalAuthSecretProvider secretProvider, Clock clock) {
        this.secretProvider = secretProvider;
        this.clock = clock;
    }

    public Map<String, String> signedHeaders(String method, String path, AuthContext context) {
        Map<String, String> headers = authHeaders(context);
        String timestamp = Instant.now(clock).toString();
        String nonce = UUID.randomUUID().toString();
        headers.put(InternalAuthHeaders.TIMESTAMP, timestamp);
        headers.put(InternalAuthHeaders.NONCE, nonce);
        headers.put(InternalAuthHeaders.ISSUER, secretProvider.issuer());
        String canonical = InternalAuthCanonicalizer.canonical(method, path, timestamp, nonce, headers);
        headers.put(InternalAuthHeaders.SIGNATURE, InternalAuthCrypto.hmac(secretProvider.secret(), canonical));
        return headers;
    }

    private Map<String, String> authHeaders(AuthContext context) {
        Map<String, String> headers = new LinkedHashMap<>();
        put(headers, InternalAuthHeaders.CHANNEL, context.channel());
        put(headers, InternalAuthHeaders.PRINCIPAL_ID, context.principalId());
        put(headers, InternalAuthHeaders.USER_ID, context.userId());
        put(headers, InternalAuthHeaders.CUSTOMER_ID, context.customerId());
        put(headers, InternalAuthHeaders.SESSION_ID, context.sessionId());
        put(headers, InternalAuthHeaders.DEVICE_ID, context.deviceId());
        put(headers, InternalAuthHeaders.TRUSTED_DEVICE, String.valueOf(context.trustedDevice()));
        put(headers, InternalAuthHeaders.ROLES, String.join(",", context.roles()));
        put(headers, InternalAuthHeaders.SCOPES, String.join(",", context.scopes()));
        put(headers, InternalAuthHeaders.DPOP_VERIFIED, String.valueOf(context.dpopVerified()));
        put(headers, InternalAuthHeaders.DPOP_JKT, context.dpopJkt());
        put(headers, InternalAuthHeaders.DPOP_JTI, context.dpopJti());
        return headers;
    }

    private void put(Map<String, String> headers, String name, String value) {
        headers.put(name, value == null ? "" : value);
    }
}
