package vn.com.truongsonbank.shared.security;

public final class InternalAuthHeaders {
    public static final String CHANNEL = "X-Auth-Channel";
    public static final String PRINCIPAL_ID = "X-Auth-Principal-Id";
    public static final String USER_ID = "X-Auth-User-Id";
    public static final String CUSTOMER_ID = "X-Auth-Customer-Id";
    public static final String SESSION_ID = "X-Auth-Session-Id";
    public static final String DEVICE_ID = "X-Auth-Device-Id";
    public static final String TRUSTED_DEVICE = "X-Auth-Trusted-Device";
    public static final String ROLES = "X-Auth-Roles";
    public static final String SCOPES = "X-Auth-Scopes";
    public static final String DPOP_VERIFIED = "X-DPoP-Verified";
    public static final String DPOP_JKT = "X-DPoP-Jkt";
    public static final String DPOP_JTI = "X-DPoP-Jti";
    public static final String TIMESTAMP = "X-Auth-Timestamp";
    public static final String NONCE = "X-Auth-Nonce";
    public static final String ISSUER = "X-Auth-Issuer";
    public static final String SIGNATURE = "X-Auth-Signature";

    private InternalAuthHeaders() {
    }
}
