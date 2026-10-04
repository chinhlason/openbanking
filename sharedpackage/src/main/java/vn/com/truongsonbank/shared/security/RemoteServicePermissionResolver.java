package vn.com.truongsonbank.shared.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import vn.com.truongsonbank.shared.protocol.ProtocolProperties;

final class RemoteServicePermissionResolver implements ServicePermissionResolver {
    private final RestClient client;
    private final ServiceAuthProperties.PermissionResolver properties;
    private final ServiceAuthProperties outboundProperties;
    private final ServiceTokenManager tokenManager;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    RemoteServicePermissionResolver(RestClient.Builder builder, ServiceAuthProperties properties, ServiceTokenManager tokenManager) {
        this.client = builder.baseUrl(properties.getPermissionResolver().getBaseUrl()).build();
        this.properties = properties.getPermissionResolver();
        this.outboundProperties = properties;
        this.tokenManager = tokenManager;
    }

    @Override
    public ServicePermissions resolve(String serviceCode) {
        Cached current = cache.get(serviceCode);
        if (current != null && Instant.now().isBefore(current.expiresAt())) return current.permissions();
        try {
            ProtocolProperties.ServiceAuth auth = new ProtocolProperties.ServiceAuth();
            auth.setEnabled(true);
            auth.setClientId(outboundProperties.getClientId());
            auth.setClientSecret(outboundProperties.getClientSecret());
            auth.setTokenUri(outboundProperties.getTokenUri());
            auth.setAudience(outboundProperties.getAudience() == null ? "common-service" : outboundProperties.getAudience());
            String token = tokenManager.getToken(auth);
            Map<String, Object> body = client.get()
                    .uri(properties.getPath(), "SERVICE", serviceCode)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            Map<String, Object> payload = unwrap(body);
            ServicePermissions permissions = new ServicePermissions(values(payload, "allow"), values(payload, "deny"), number(payload, "version"));
            cache.put(serviceCode, new Cached(permissions, Instant.now().plus(properties.getCacheTtl())));
            return permissions;
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new vn.com.truongsonbank.shared.exception.TsbException(ServiceAuthErrors.PERMISSION_DENIED, exception);
        } catch (RuntimeException exception) {
            throw new vn.com.truongsonbank.shared.exception.TsbException(ServiceAuthErrors.PERMISSION_UNAVAILABLE, exception);
        }
    }

    private java.util.List<String> values(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value instanceof java.util.List<?> list ? list.stream().map(String::valueOf).toList() : java.util.List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrap(Map<String, Object> body) {
        if (body == null) return Map.of();
        Object data = body.get("data");
        return data instanceof Map<?, ?> map ? (Map<String, Object>) map : body;
    }

    private long number(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? 0 : Long.parseLong(String.valueOf(value));
    }

    @Override
    public void evict(String serviceCode) { cache.remove(serviceCode); }

    @Override
    public void evictAll() { cache.clear(); }
    private record Cached(ServicePermissions permissions, Instant expiresAt) { }
}
