package vn.com.truongsonbank.shared.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Adds trusted identity context to the current span without changing the trace id. */
public final class TraceIdentityEnricher {
    public static final String USER_ID_ATTRIBUTE = "tsb.user.id";
    public static final String CUSTOMER_ID_ATTRIBUTE = "tsb.customer.id";

    private final Tracer tracer;
    private final TracingProperties properties;

    TraceIdentityEnricher(Tracer tracer, TracingProperties properties) {
        this.tracer = tracer;
        this.properties = properties;
    }

    public void enrich(String userId, String customerId) {
        TracingProperties.Identity identity = properties.getIdentity();
        if (!identity.isEnabled()) {
            return;
        }
        Span span = tracer.currentSpan();
        if (span == null) {
            return;
        }
        tag(span, USER_ID_ATTRIBUTE, userId, identity);
        tag(span, CUSTOMER_ID_ATTRIBUTE, customerId, identity);
    }

    private void tag(Span span, String key, String value, TracingProperties.Identity identity) {
        if (value == null || value.isBlank()) {
            return;
        }
        span.tag(key, identity.isRawValues() ? value : pseudonymize(value, identity.getHashKey()));
    }

    private String pseudonymize(String value, String hashKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "hmac:v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not pseudonymize trace identity", exception);
        }
    }
}
