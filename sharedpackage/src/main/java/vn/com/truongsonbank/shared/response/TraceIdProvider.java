package vn.com.truongsonbank.shared.response;

import jakarta.servlet.http.HttpServletRequest;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;

public class TraceIdProvider {
    private static final String TRACEPARENT_HEADER = "traceparent";
    private static final String[] MDC_KEYS = {"traceId", "trace_id"};
    private final ObjectProvider<Tracer> tracer;

    public TraceIdProvider(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    public String resolve(HttpServletRequest request) {
        String mdcTraceId = fromMdc();
        if (hasText(mdcTraceId)) {
            return mdcTraceId;
        }

        if (request != null) {
            Object attribute = request.getAttribute(ResponseWrapperFilter.TRACE_ID);
            if (attribute != null && hasText(attribute.toString())) {
                return attribute.toString();
            }
        }

        String currentTraceId = fromCurrentSpan();
        if (hasText(currentTraceId)) {
            return currentTraceId;
        }

        if (request == null) {
            return null;
        }

        return fromTraceparent(request.getHeader(TRACEPARENT_HEADER));
    }

    private String fromCurrentSpan() {
        Tracer currentTracer = tracer.getIfAvailable();
        if (currentTracer == null) {
            return null;
        }
        Span currentSpan = currentTracer.currentSpan();
        return currentSpan == null ? null : currentSpan.context().traceId();
    }

    private static String fromMdc() {
        for (String key : MDC_KEYS) {
            String value = MDC.get(key);
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String fromTraceparent(String traceparent) {
        if (!hasText(traceparent)) {
            return null;
        }
        String[] parts = traceparent.split("-");
        if (parts.length != 4) {
            return null;
        }
        String traceId = parts[1];
        return isValidTraceId(traceId) ? traceId : null;
    }

    private static boolean isValidTraceId(String traceId) {
        if (traceId.length() != 32 || "00000000000000000000000000000000".equals(traceId)) {
            return false;
        }
        for (int i = 0; i < traceId.length(); i++) {
            if (Character.digit(traceId.charAt(i), 16) == -1) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
