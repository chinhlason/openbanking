package vn.com.truongsonbank.shared.security;

import java.util.Locale;
import java.util.Map;

final class InternalAuthCanonicalizer {
    private static final String[] SIGNED_HEADERS = {
            InternalAuthHeaders.CHANNEL,
            InternalAuthHeaders.PRINCIPAL_ID,
            InternalAuthHeaders.USER_ID,
            InternalAuthHeaders.CUSTOMER_ID,
            InternalAuthHeaders.SESSION_ID,
            InternalAuthHeaders.DEVICE_ID,
            InternalAuthHeaders.TRUSTED_DEVICE,
            InternalAuthHeaders.ROLES,
            InternalAuthHeaders.SCOPES,
            InternalAuthHeaders.DPOP_VERIFIED,
            InternalAuthHeaders.DPOP_JKT,
            InternalAuthHeaders.DPOP_JTI
    };

    private InternalAuthCanonicalizer() {
    }

    static String canonical(String method, String path, String timestamp, String nonce, Map<String, String> headers) {
        StringBuilder builder = new StringBuilder()
                .append(nullToEmpty(method).toUpperCase(Locale.ROOT)).append('\n')
                .append(nullToEmpty(path)).append('\n')
                .append(nullToEmpty(timestamp)).append('\n')
                .append(nullToEmpty(nonce));
        for (String header : SIGNED_HEADERS) {
            builder.append('\n')
                    .append(header.toLowerCase(Locale.ROOT))
                    .append(':')
                    .append(nullToEmpty(headers.get(header)));
        }
        return builder.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
