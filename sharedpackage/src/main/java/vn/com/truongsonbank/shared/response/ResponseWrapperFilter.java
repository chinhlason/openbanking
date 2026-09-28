package vn.com.truongsonbank.shared.response;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class ResponseWrapperFilter extends OncePerRequestFilter {
    public static final String START_NANOS = ResponseWrapperFilter.class.getName() + ".START_NANOS";
    public static final String TRACE_ID = ResponseWrapperFilter.class.getName() + ".TRACE_ID";
    private static final String TRACE_HEADER = "X-Trace-Id";
    private final TraceIdProvider traceIdProvider;

    ResponseWrapperFilter(TraceIdProvider traceIdProvider) {
        this.traceIdProvider = traceIdProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = traceIdProvider.resolve(request);
        request.setAttribute(START_NANOS, System.nanoTime());
        String previousTraceId = MDC.get("traceId");
        String previousTrace_id = MDC.get("trace_id");
        try {
            if (traceId != null && !traceId.isBlank()) {
                request.setAttribute(TRACE_ID, traceId);
                response.setHeader(TRACE_HEADER, traceId);
                MDC.put("traceId", traceId);
                MDC.put("trace_id", traceId);
            }
            filterChain.doFilter(request, response);
        } finally {
            if (previousTraceId == null) {
                MDC.remove("traceId");
            } else {
                MDC.put("traceId", previousTraceId);
            }
            if (previousTrace_id == null) {
                MDC.remove("trace_id");
            } else {
                MDC.put("trace_id", previousTrace_id);
            }
        }
    }
}
