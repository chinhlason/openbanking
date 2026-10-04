package vn.com.truongsonbank.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.com.truongsonbank.shared.exception.TsbException;
import vn.com.truongsonbank.shared.response.TsbResponse;
import vn.com.truongsonbank.shared.response.TraceIdProvider;
import vn.com.truongsonbank.shared.tracing.TraceIdentityEnricher;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InternalAuthVerificationFilter extends OncePerRequestFilter {
    private final InternalAuthProperties properties;
    private final InternalAuthSecretProvider secretProvider;
    private final InternalAuthNonceStore nonceStore;
    private final TraceIdProvider traceIdProvider;
    private final TraceIdentityEnricher traceIdentityEnricher;
    private final ObjectMapper objectMapper;

    InternalAuthVerificationFilter(
            InternalAuthProperties properties,
            InternalAuthSecretProvider secretProvider,
            InternalAuthNonceStore nonceStore,
            TraceIdProvider traceIdProvider,
            TraceIdentityEnricher traceIdentityEnricher,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.secretProvider = secretProvider;
        this.nonceStore = nonceStore;
        this.traceIdProvider = traceIdProvider;
        this.traceIdentityEnricher = traceIdentityEnricher;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return properties.getExcludedPaths().stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            AuthContext context = verify(request);
            AuthContextHolder.set(context);
            traceIdentityEnricher.enrich(context.userId(), context.customerId());
            filterChain.doFilter(request, response);
        } catch (TsbException exception) {
            writeError(request, response, exception);
        } finally {
            AuthContextHolder.clear();
        }
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, TsbException exception)
            throws IOException {
        String traceId = traceIdProvider.resolve(request);
        response.setStatus(exception.error().httpStatus());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        if (traceId != null && !traceId.isBlank()) {
            response.setHeader("X-Trace-Id", traceId);
        }
        objectMapper.writeValue(response.getWriter(), new TsbResponse<>(
                traceId,
                false,
                exception.error().code(),
                exception.error().defaultMessage(),
                null,
                java.time.Instant.now(),
                null));
    }

    private AuthContext verify(HttpServletRequest request) {
        String timestamp = required(request, InternalAuthHeaders.TIMESTAMP);
        String nonce = required(request, InternalAuthHeaders.NONCE);
        String issuer = required(request, InternalAuthHeaders.ISSUER);
        String signature = required(request, InternalAuthHeaders.SIGNATURE);
        validateTimestamp(timestamp);
        if (!nonceStore.markIfNew(issuer, nonce, secretProvider.maxSkew())) {
            throw new TsbException(InternalAuthErrors.REPLAY);
        }

        Map<String, String> headers = signedHeaders(request);
        String canonical = InternalAuthCanonicalizer.canonical(request.getMethod(), request.getRequestURI(), timestamp, nonce, headers);
        String expected = InternalAuthCrypto.hmac(secretProvider.secret(), canonical);
        if (!InternalAuthCrypto.equals(expected, signature)) {
            throw new TsbException(InternalAuthErrors.INVALID_SIGNATURE);
        }
        return context(headers);
    }

    private void validateTimestamp(String timestamp) {
        try {
            Instant value = Instant.parse(timestamp);
            Duration age = Duration.between(value, Instant.now()).abs();
            if (age.compareTo(secretProvider.maxSkew()) > 0) {
                throw new TsbException(InternalAuthErrors.INVALID_SIGNATURE);
            }
        } catch (TsbException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TsbException(InternalAuthErrors.INVALID_SIGNATURE);
        }
    }

    private String required(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        if (value == null || value.isBlank()) {
            throw new TsbException(InternalAuthErrors.MISSING);
        }
        return value;
    }

    private Map<String, String> signedHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        put(headers, request, InternalAuthHeaders.CHANNEL);
        put(headers, request, InternalAuthHeaders.PRINCIPAL_ID);
        put(headers, request, InternalAuthHeaders.USER_ID);
        put(headers, request, InternalAuthHeaders.CUSTOMER_ID);
        put(headers, request, InternalAuthHeaders.SESSION_ID);
        put(headers, request, InternalAuthHeaders.DEVICE_ID);
        put(headers, request, InternalAuthHeaders.TRUSTED_DEVICE);
        put(headers, request, InternalAuthHeaders.ROLES);
        put(headers, request, InternalAuthHeaders.SCOPES);
        put(headers, request, InternalAuthHeaders.ENTITLEMENTS);
        put(headers, request, InternalAuthHeaders.ENTITLEMENT_VERSION);
        put(headers, request, InternalAuthHeaders.DPOP_VERIFIED);
        put(headers, request, InternalAuthHeaders.DPOP_JKT);
        put(headers, request, InternalAuthHeaders.DPOP_JTI);
        return headers;
    }

    private void put(Map<String, String> headers, HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        headers.put(header, value == null ? "" : value);
    }

    private AuthContext context(Map<String, String> headers) {
        return new AuthContext(
                headers.get(InternalAuthHeaders.CHANNEL),
                headers.get(InternalAuthHeaders.PRINCIPAL_ID),
                headers.get(InternalAuthHeaders.USER_ID),
                headers.get(InternalAuthHeaders.CUSTOMER_ID),
                headers.get(InternalAuthHeaders.SESSION_ID),
                headers.get(InternalAuthHeaders.DEVICE_ID),
                Boolean.parseBoolean(headers.get(InternalAuthHeaders.TRUSTED_DEVICE)),
                csv(headers.get(InternalAuthHeaders.ROLES)),
                csv(headers.get(InternalAuthHeaders.SCOPES)),
                csv(headers.get(InternalAuthHeaders.ENTITLEMENTS)),
                version(headers.get(InternalAuthHeaders.ENTITLEMENT_VERSION)),
                Boolean.parseBoolean(headers.get(InternalAuthHeaders.DPOP_VERIFIED)),
                headers.get(InternalAuthHeaders.DPOP_JKT),
                headers.get(InternalAuthHeaders.DPOP_JTI));
    }

    private List<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private long version(String value) {
        try {
            return value == null || value.isBlank() ? 0L : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
