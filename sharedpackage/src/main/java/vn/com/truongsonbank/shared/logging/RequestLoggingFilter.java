package vn.com.truongsonbank.shared.logging;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import vn.com.truongsonbank.shared.response.TraceIdProvider;

class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final java.util.List<String> BUILT_IN_HEALTHCHECK_PATHS = java.util.List.of(
            "/actuator/prometheus",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/metrics",
            "/actuator/metrics/**");
    private final LoggingProperties properties;
    private final SensitiveDataMasker masker;
    private final TraceIdProvider traceIdProvider;
    private final ObjectMapper objectMapper;

    RequestLoggingFilter(
            LoggingProperties properties,
            SensitiveDataMasker masker,
            TraceIdProvider traceIdProvider,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.masker = masker;
        this.traceIdProvider = traceIdProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!properties.getRequest().isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (isExcluded(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        long startNanos = System.nanoTime();
        HttpServletRequest requestToUse = properties.getRequest().isIncludeBody()
                ? new ContentCachingRequestWrapper(request, Math.max(properties.getRequest().getMaxBodyLength(), 0))
                : request;
        String traceId = traceIdProvider.resolve(requestToUse);
        String previousTraceId = MDC.get("traceId");
        String previousTrace_id = MDC.get("trace_id");
        try {
            if (traceId != null && !traceId.isBlank()) {
                MDC.put("traceId", traceId);
                MDC.put("trace_id", traceId);
            }
            filterChain.doFilter(requestToUse, response);
        } finally {
            log.info(toJson(requestLog(requestToUse, response, startNanos)));
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

    private Map<String, Object> requestLog(HttpServletRequest request, HttpServletResponse response, long startNanos) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "http_request");
        event.put("timestamp", Instant.now().toString());
        event.put("traceId", traceIdProvider.resolve(request));
        event.put("method", request.getMethod());
        event.put("path", request.getRequestURI());
        event.put("status", response.getStatus());
        event.put("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
        event.put("clientIp", request.getRemoteAddr());

        if (properties.getRequest().isIncludeQueryString()) {
            event.put("query", maskedQuery(request));
        }
        if (properties.getRequest().isIncludeHeaders()) {
            event.put("headers", maskedHeaders(request));
        }
        if (properties.getRequest().isIncludeBody() && request instanceof ContentCachingRequestWrapper wrapper) {
            event.put("body", body(wrapper));
        }
        return event;
    }

    private boolean isExcluded(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!properties.getRequest().isHealthcheckLogEnabled() && matches(path, BUILT_IN_HEALTHCHECK_PATHS)) {
            return true;
        }
        return matches(path, properties.getRequest().getExcludePaths());
    }

    private boolean matches(String path, java.util.List<String> patterns) {
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            if (pattern.endsWith("/**")) {
                String prefix = pattern.substring(0, pattern.length() - 3);
                if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                    return true;
                }
                continue;
            }
            if (path.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, String> maskedQuery(HttpServletRequest request) {
        Map<String, String> query = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            String value = values == null || values.length == 0 ? null : values[0];
            query.put(key, masker.mask(key, value));
        });
        return query;
    }

    private Map<String, String> maskedHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        request.getHeaderNames().asIterator().forEachRemaining(name -> headers.put(name, masker.mask(name, request.getHeader(name))));
        return headers;
    }

    private String body(ContentCachingRequestWrapper request) {
        byte[] bytes = request.getContentAsByteArray();
        int max = Math.max(properties.getRequest().getMaxBodyLength(), 0);
        int length = Math.min(bytes.length, max);
        String body = new String(bytes, 0, length, request.getCharacterEncoding() == null
                ? java.nio.charset.StandardCharsets.UTF_8
                : java.nio.charset.Charset.forName(request.getCharacterEncoding()));
        String maskedBody = maskJsonBody(body);
        return bytes.length > length ? maskedBody + "...[truncated]" : maskedBody;
    }

    private String maskJsonBody(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            maskJsonNode(node);
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException e) {
            return body;
        }
    }

    private void maskJsonNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            for (Map.Entry<String, JsonNode> entry : objectNode.properties()) {
                JsonNode child = entry.getValue();
                if (child != null && child.isValueNode()) {
                    objectNode.set(entry.getKey(), objectMapper.valueToTree(masker.mask(entry.getKey(), child.asString())));
                } else {
                    maskJsonNode(child);
                }
            }
            return;
        }
        if (node instanceof ArrayNode arrayNode) {
            for (JsonNode child : arrayNode) {
                maskJsonNode(child);
            }
        }
    }

    private String toJson(Map<String, Object> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            return event.toString();
        }
    }
}
