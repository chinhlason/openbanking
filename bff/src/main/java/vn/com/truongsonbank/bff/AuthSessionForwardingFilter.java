package vn.com.truongsonbank.bff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.com.truongsonbank.shared.security.AuthContext;
import vn.com.truongsonbank.shared.security.AuthHeaderSigner;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

@Component
class AuthSessionForwardingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AuthSessionForwardingFilter.class);
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String authBaseUrl;
    private final String internalSecret;
    private final String commonBaseUrl;
    private final String commonAdminKey;
    private final AuthHeaderSigner authHeaderSigner;
    private final Map<String, MetadataCacheEntry> metadataCache = new ConcurrentHashMap<>();

    AuthSessionForwardingFilter(RestClient.Builder builder,
                                @Value("${tsb.bff.auth.base-url:http://localhost:8084/auth/api}") String authBaseUrl,
                                @Value("${tsb.bff.auth.internal-secret:local-onboarding-secret}") String internalSecret,
                                @Value("${tsb.bff.entitlement.common-url:http://localhost:8083/common/api}") String commonBaseUrl,
                                @Value("${tsb.bff.entitlement.admin-key:local-admin-key}") String commonAdminKey,
                                AuthHeaderSigner authHeaderSigner) {
        this.restClient = builder.build();
        this.objectMapper = new ObjectMapper();
        this.authBaseUrl = authBaseUrl;
        this.internalSecret = internalSecret;
        this.commonBaseUrl = commonBaseUrl;
        this.commonAdminKey = commonAdminKey;
        this.authHeaderSigner = authHeaderSigner;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String sessionId = request.getHeader("X-Session-Id");
        if (sessionId == null || sessionId.isBlank() || request.getRequestURI().contains("/auth/")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            AuthSession session = introspect(sessionId);
            MutableHeaderRequestWrapper wrapped = new MutableHeaderRequestWrapper(request);
            wrapped.putHeader("X-Auth-Subject", session.subject());
            wrapped.putHeader("X-Auth-Username", session.username());
            wrapped.putHeader("X-Auth-Device-Id", session.deviceId());
            wrapped.putHeader("X-Auth-Roles", String.join(",", session.roles() == null ? List.of() : session.roles()));
            wrapped.putHeader("X-Auth-Trusted-Device", Boolean.toString(session.trustedDevice()));
            EntitlementMetadata metadata = resolveMetadata(session.servicePackages());
            AuthContext context = new AuthContext(
                    "MOBILE", session.subject(), session.subject(), session.customerId(), sessionId,
                    session.deviceId(), session.trustedDevice(),
                    session.roles(), List.of(), metadata.allow(), metadata.version(), false, null, null);
            String signingPath = downstreamPath(request);
            authHeaderSigner.signedHeaders(request.getMethod(), signingPath, context)
                    .forEach(wrapped::putHeader);
            chain.doFilter(wrapped, response);
        } catch (RuntimeException ex) {
            log.warn("Could not enrich authenticated request path={}: {}", request.getRequestURI(), ex.getMessage(), ex);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    private AuthSession introspect(String sessionId) {
        String body = restClient.post()
                .uri(authBaseUrl + "/v1/internal/sessions/introspect")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Internal-Onboarding-Secret", internalSecret)
                .body(new IntrospectRequest(sessionId))
                .retrieve()
                .body(String.class);
        JsonNode root = readJson(body);
        JsonNode data = root == null ? null : root.path("data");
        if (data == null || data.isMissingNode() || data.isNull()) {
            throw new IllegalStateException("Invalid auth introspection response");
        }
        return objectMapper.convertValue(data, AuthSession.class);
    }

    private EntitlementMetadata resolveMetadata(List<String> packages) {
        List<String> packageCodes = packages == null ? List.of() : packages;
        List<String> allow = new ArrayList<>();
        List<String> deny = new ArrayList<>();
        long version = 0L;
        for (String packageCode : packageCodes) {
            MetadataCacheEntry cached = metadataCache.get(packageCode);
            if (cached == null || cached.expiresAt().isBefore(Instant.now())) {
                cached = loadMetadata(packageCode);
                metadataCache.put(packageCode, cached);
            }
            allow.addAll(cached.metadata().allow());
            deny.addAll(cached.metadata().deny());
            version = Math.max(version, cached.metadata().version());
        }
        deny.forEach(allow::remove);
        return new EntitlementMetadata(allow.stream().distinct().toList(), deny.stream().distinct().toList(), version);
    }

    private MetadataCacheEntry loadMetadata(String packageCode) {
        String body = restClient.get()
                .uri(commonBaseUrl + "/entitlements/admin/metadata/packages/" + packageCode)
                .header("X-Config-Admin-Key", commonAdminKey)
                .retrieve().body(String.class);
        JsonNode root = readJson(body);
        JsonNode data = root == null ? null : root.path("data");
        if (data == null || data.isMissingNode() || data.isNull()) {
            throw new IllegalStateException("Missing entitlement metadata for package " + packageCode);
        }
        EntitlementMetadata metadata = objectMapper.convertValue(data, EntitlementMetadata.class);
        return new MetadataCacheEntry(metadata, Instant.now().plus(Duration.ofSeconds(60)));
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid downstream JSON response", ex);
        }
    }

    private String downstreamPath(HttpServletRequest request) {
        String path = request.getRequestURI().replaceFirst("^" + request.getContextPath(), "");
        if (path.startsWith("/client/")) {
            return "/" + path.substring("/client/".length());
        }
        if (path.startsWith("/common/")) {
            return "/common/api/" + path.substring("/common/".length());
        }
        if (path.startsWith("/auth/")) {
            return "/auth/api/" + path.substring("/auth/".length());
        }
        return path;
    }

    private record IntrospectRequest(String sessionId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AuthSession(String subject, String customerId, String username, String deviceId, boolean trustedDevice, List<String> roles,
                               List<String> servicePackages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EntitlementMetadata(List<String> allow, List<String> deny, long version) {
        private EntitlementMetadata {
            allow = allow == null ? List.of() : allow;
            deny = deny == null ? List.of() : deny;
        }
    }

    private record MetadataCacheEntry(EntitlementMetadata metadata, Instant expiresAt) {
    }
}
