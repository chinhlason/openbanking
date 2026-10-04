package vn.com.truongsonbank.shared.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import vn.com.truongsonbank.shared.protocol.ProtocolProperties;

public class ServiceTokenManager {
    private final RestClient restClient;
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    public ServiceTokenManager(RestClient.Builder builder, ObjectProvider<MeterRegistry> meterRegistry) {
        this.restClient = builder.build();
        this.meterRegistry = meterRegistry.getIfAvailable();
    }

    public String getToken(ProtocolProperties.ServiceAuth config) {
        validate(config);
        String key = config.getClientId() + "|" + config.getAudience() + "|" + config.getTokenUri();
        Token cached = tokens.get(key);
        Instant refreshAt = cached == null ? Instant.MIN : cached.expiresAt().minus(config.getTokenRefreshSkew());
        if (cached != null && Instant.now().isBefore(refreshAt)) {
            count("tsb.service.token.cache.hit");
            return cached.value();
        }
        synchronized (locks.computeIfAbsent(key, ignored -> new Object())) {
            cached = tokens.get(key);
            refreshAt = cached == null ? Instant.MIN : cached.expiresAt().minus(config.getTokenRefreshSkew());
            if (cached != null && Instant.now().isBefore(refreshAt)) {
                count("tsb.service.token.cache.hit");
                return cached.value();
            }
            count("tsb.service.token.cache.miss");
            try {
                return fetch(key, config);
            } catch (RuntimeException exception) {
                throw new vn.com.truongsonbank.shared.exception.TsbException(ServiceAuthErrors.TOKEN_FETCH_FAILED, exception);
            }
        }
    }

    public void evict(ProtocolProperties.ServiceAuth config) {
        if (config != null) tokens.remove(config.getClientId() + "|" + config.getAudience() + "|" + config.getTokenUri());
    }

    @SuppressWarnings("unchecked")
    private String fetch(String key, ProtocolProperties.ServiceAuth config) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", config.getClientId());
        form.add("client_secret", config.getClientSecret());
        form.add("audience", config.getAudience());
        Map<String, Object> response = restClient.post().uri(config.getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        if (response == null || response.get("access_token") == null) throw new IllegalStateException("Keycloak returned no access token");
        long expiresIn = Long.parseLong(String.valueOf(response.getOrDefault("expires_in", 60)));
        String token = String.valueOf(response.get("access_token"));
        tokens.put(key, new Token(token, Instant.now().plusSeconds(Math.max(1, expiresIn))));
        return token;
    }

    private void validate(ProtocolProperties.ServiceAuth config) {
        if (config == null || !config.isEnabled() || blank(config.getClientId()) || blank(config.getClientSecret())
                || blank(config.getTokenUri()) || blank(config.getAudience())) {
            throw new vn.com.truongsonbank.shared.exception.TsbException(ServiceAuthErrors.TOKEN_FETCH_FAILED);
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private void count(String name) {
        if (meterRegistry != null) meterRegistry.counter(name).increment();
    }

    private record Token(String value, Instant expiresAt) { }
}
