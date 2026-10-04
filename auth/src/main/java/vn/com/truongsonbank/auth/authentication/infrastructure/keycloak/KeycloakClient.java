package vn.com.truongsonbank.auth.authentication.infrastructure.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;
import vn.com.truongsonbank.auth.authentication.config.AuthProperties;
import vn.com.truongsonbank.auth.authentication.domain.AuthErrors;
import vn.com.truongsonbank.shared.exception.BusinessException;
import vn.com.truongsonbank.shared.exception.UnauthorizedException;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class KeycloakClient {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final RestClient restClient;
    private final AuthProperties properties;

    KeycloakClient(RestClient.Builder builder, AuthProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public String passwordGrant(String username, String pin) {
        return passwordGrant(username, pin, null);
    }

    public String passwordGrant(String username, String pin, String dpopProof) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", properties.getKeycloak().getMobileClientId());
        form.add("username", username);
        form.add("password", pin);

        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(tokenUri(dpopProof))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED);
            if (dpopProof != null && !dpopProof.isBlank()) {
                request.header("DPoP", dpopProof);
            }
            KeycloakTokenResponse response = request.body(form).retrieve().body(KeycloakTokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new BusinessException(AuthErrors.KEYCLOAK_PASSWORD_INVALID);
            }
            return response.accessToken();
        } catch (RestClientResponseException ex) {
            throw loginGrantError("password", ex);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(AuthErrors.KEYCLOAK_GRANT_REJECTED, "Keycloak token endpoint is unavailable");
        }
    }

    public String pinGrant(String username, String pin, String dpopProof) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "tsb-pin");
        form.add("client_id", properties.getKeycloak().getMobileClientId());
        form.add("username", username);
        form.add("pin", pin);

        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(tokenUri(dpopProof))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED);
            if (dpopProof != null && !dpopProof.isBlank()) {
                request.header("DPoP", dpopProof);
            }
            KeycloakTokenResponse response = request.body(form).retrieve().body(KeycloakTokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new BusinessException(AuthErrors.KEYCLOAK_PIN_INVALID);
            }
            return response.accessToken();
        } catch (RestClientResponseException ex) {
            throw loginGrantError("tsb-pin", ex);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(AuthErrors.KEYCLOAK_GRANT_REJECTED, "Keycloak token endpoint is unavailable");
        }
    }

    public String biometricGrant(String username, String deviceId, String challengeId, String nonce, String signature, String dpopProof) {
        return credentialGrant("biometric", username, deviceId, challengeId, nonce, signature, dpopProof);
    }

    public String passkeyGrant(String username, String deviceId, String challengeId, String nonce, String signature, String dpopProof) {
        return credentialGrant("passkey", username, deviceId, challengeId, nonce, signature, dpopProof);
    }

    private String credentialGrant(String grantType, String username, String deviceId, String challengeId, String nonce, String signature, String dpopProof) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", grantType);
        form.add("client_id", properties.getKeycloak().getMobileClientId());
        form.add("username", username);
        form.add("device_id", deviceId);
        form.add("challenge_id", challengeId);
        form.add("nonce", nonce);
        form.add("signature", signature);

        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(tokenUri(dpopProof))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED);
            if (dpopProof != null && !dpopProof.isBlank()) {
                request.header("DPoP", dpopProof);
            }
            KeycloakTokenResponse response = request.body(form).retrieve().body(KeycloakTokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw invalidCredential(grantType);
            }
            return response.accessToken();
        } catch (RestClientResponseException ex) {
            throw loginGrantError(grantType, ex);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(AuthErrors.KEYCLOAK_GRANT_REJECTED, "Keycloak token endpoint is unavailable");
        }
    }

    private BusinessException loginGrantError(String grantType, RestClientResponseException ex) {
        String description = keycloakErrorDescription(ex);
        if ("password".equals(grantType)) {
            return new BusinessException(AuthErrors.KEYCLOAK_PASSWORD_INVALID);
        }
        if ("tsb-pin".equals(grantType)) {
            return new BusinessException(AuthErrors.KEYCLOAK_PIN_INVALID);
        }
        if ("biometric".equals(grantType)) {
            return new BusinessException(AuthErrors.KEYCLOAK_BIOMETRIC_INVALID);
        }
        if ("passkey".equals(grantType)) {
            return new BusinessException(AuthErrors.KEYCLOAK_PASSKEY_INVALID);
        }
        return new BusinessException(AuthErrors.KEYCLOAK_GRANT_REJECTED, description);
    }

    private BusinessException invalidCredential(String grantType) {
        if ("biometric".equals(grantType)) {
            return new BusinessException(AuthErrors.KEYCLOAK_BIOMETRIC_INVALID);
        }
        if ("passkey".equals(grantType)) {
            return new BusinessException(AuthErrors.KEYCLOAK_PASSKEY_INVALID);
        }
        return new BusinessException(AuthErrors.KEYCLOAK_PASSWORD_INVALID);
    }

    private String keycloakErrorDescription(RestClientResponseException ex) {
        try {
            JsonNode body = OBJECT_MAPPER.readTree(ex.getResponseBodyAsString());
            String description = body.path("error_description").asText("");
            if (!description.isBlank()) {
                return description;
            }
            String error = body.path("error").asText("");
            return error.isBlank() ? "HTTP " + ex.getStatusCode().value() : error;
        } catch (Exception ignored) {
            return "HTTP " + ex.getStatusCode().value();
        }
    }

    public String createUserWithPinIfAbsent(String username, String pin) {
        String token = adminToken();
        String userId = findUserId(username, token);
        boolean created = userId == null;
        if (userId == null) {
            userId = createUser(username, token);
        }
        if (created) {
            String password = generatedPassword();
            setPassword(userId, password, token);
            sendDefaultPassword(username, password);
        }
        updatePinCredential(username, pin);
        clearRequiredActions(userId, username, token);
        return userId;
    }

    public void enableBiometricCredential(String username, String deviceId, String publicKey) {
        updateBiometricCredential(username, deviceId, publicKey, true);
    }

    public void disableBiometricCredential(String username, String deviceId) {
        updateBiometricCredential(username, deviceId, null, false);
    }

    public void enablePasskeyCredential(String username, String deviceId, String publicKey) {
        updateCredential(username, deviceId, publicKey, true, "tsb-passkey");
    }

    public void disablePasskeyCredential(String username, String deviceId) {
        updateCredential(username, deviceId, null, false, "tsb-passkey");
    }

    public boolean hasBiometricCredential(String username, String deviceId) {
        return hasCredential(username, deviceId, "tsb-biometric");
    }

    public boolean hasPasskeyCredential(String username, String deviceId) {
        return hasCredential(username, deviceId, "tsb-passkey");
    }

    private boolean hasCredential(String username, String deviceId, String credentialType) {
        Map<?, ?> response = restClient.get()
                .uri(properties.getKeycloak().getAdminBaseUrl() + "/realms/" + properties.getKeycloak().getRealm()
                        + "/tsb-biometric/credentials?username={username}&deviceId={deviceId}&credentialType={credentialType}",
                        username, deviceId, credentialType)
                .header("X-Keycloak-Biometric-Secret", properties.getBiometricGrantSecret())
                .retrieve()
                .body(Map.class);
        return Boolean.TRUE.equals(response == null ? null : response.get("exists"));
    }

    private String adminToken() {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", properties.getKeycloak().getAdminClientId());
        form.add("username", properties.getKeycloak().getAdminUsername());
        form.add("password", properties.getKeycloak().getAdminPassword());
        KeycloakTokenResponse response = restClient.post()
                .uri(properties.getKeycloak().getAdminBaseUrl() + "/realms/" + properties.getKeycloak().getAdminRealm() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KeycloakTokenResponse.class);
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new UnauthorizedException();
        }
        return response.accessToken();
    }

    private String tokenUri(String dpopProof) {
        if (dpopProof == null || dpopProof.isBlank()) {
            return properties.getKeycloak().getTokenUri();
        }
        try {
            String[] parts = dpopProof.split("\\.");
            if (parts.length != 3) {
                return properties.getKeycloak().getTokenUri();
            }
            JsonNode payload = OBJECT_MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1]));
            String htu = payload.path("htu").asText("");
            URI candidate = URI.create(htu);
            URI configured = URI.create(properties.getKeycloak().getTokenUri());
            if (candidate.getPath().equals(configured.getPath()) && allowedHost(candidate.getHost())) {
                return htu;
            }
        } catch (Exception ignored) {
            return properties.getKeycloak().getTokenUri();
        }
        return properties.getKeycloak().getTokenUri();
    }

    private boolean allowedHost(String host) {
        return host != null && (host.equals("keycloak")
                || host.equals("localhost")
                || host.equals("127.0.0.1")
                || host.startsWith("192.168.")
                || host.startsWith("10.")
                || host.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*"));
    }

    @SuppressWarnings("unchecked")
    private String findUserId(String username, String token) {
        List<Map<String, Object>> users = restClient.get()
                .uri(properties.getKeycloak().getAdminBaseUrl() + "/admin/realms/" + properties.getKeycloak().getRealm()
                        + "/users?username={username}&exact=true", username)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(List.class);
        if (users == null || users.isEmpty()) {
            return null;
        }
        Object id = users.get(0).get("id");
        return id == null ? null : id.toString();
    }

    private String createUser(String username, String token) {
        try {
            restClient.post()
                    .uri(properties.getKeycloak().getAdminBaseUrl() + "/admin/realms/" + properties.getKeycloak().getRealm() + "/users")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(userProfile(username))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (!HttpStatusCode.valueOf(ex.getStatusCode().value()).isSameCodeAs(org.springframework.http.HttpStatus.CONFLICT)) {
                throw ex;
            }
        }
        String userId = findUserId(username, token);
        if (userId == null) {
            throw new UnauthorizedException();
        }
        return userId;
    }

    private void updateBiometricCredential(String username, String deviceId, String publicKey, boolean enabled) {
        updateCredential(username, deviceId, publicKey, enabled, "tsb-biometric");
    }

    private void updatePinCredential(String username, String pin) {
        restClient.post()
                .uri(properties.getKeycloak().getAdminBaseUrl() + "/realms/" + properties.getKeycloak().getRealm()
                        + "/tsb-biometric/credentials")
                .header("X-Keycloak-Biometric-Secret", properties.getBiometricGrantSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "pin", pin, "credentialType", "tsb-pin"))
                .retrieve()
                .toBodilessEntity();
    }

    private void updateCredential(String username, String deviceId, String publicKey, boolean enabled, String credentialType) {
        if (enabled) {
            restClient.post()
                    .uri(properties.getKeycloak().getAdminBaseUrl() + "/realms/" + properties.getKeycloak().getRealm()
                            + "/tsb-biometric/credentials")
                    .header("X-Keycloak-Biometric-Secret", properties.getBiometricGrantSecret())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("username", username, "deviceId", deviceId, "publicKey", publicKey, "credentialType", credentialType))
                    .retrieve()
                    .toBodilessEntity();
        } else {
            restClient.method(HttpMethod.DELETE)
                    .uri(properties.getKeycloak().getAdminBaseUrl() + "/realms/" + properties.getKeycloak().getRealm()
                            + "/tsb-biometric/credentials")
                    .header("X-Keycloak-Biometric-Secret", properties.getBiometricGrantSecret())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("username", username, "deviceId", deviceId, "credentialType", credentialType))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    private void setPassword(String userId, String pin, String token) {
        restClient.put()
                .uri(properties.getKeycloak().getAdminBaseUrl() + "/admin/realms/" + properties.getKeycloak().getRealm()
                        + "/users/{userId}/reset-password", userId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "password", "value", pin, "temporary", false))
                .retrieve()
                .toBodilessEntity();
    }

    private void clearRequiredActions(String userId, String username, String token) {
        restClient.put()
                .uri(properties.getKeycloak().getAdminBaseUrl() + "/admin/realms/" + properties.getKeycloak().getRealm()
                        + "/users/{userId}", userId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(userProfile(username))
                .retrieve()
                .toBodilessEntity();
    }

    private Map<String, Object> userProfile(String username) {
        return Map.of(
                "username", username,
                "email", username + "@truongsonbank.local",
                "firstName", username,
                "lastName", "Customer",
                "enabled", true,
                "emailVerified", true,
                "requiredActions", List.of()
        );
    }

    private String generatedPassword() {
        return UUID.randomUUID() + "-" + UUID.randomUUID();
    }

    private void sendDefaultPassword(String username, String password) {
        restClient.post()
                .uri(properties.getCommonBaseUrl() + "/3rd/sms/password/send")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("phone", username, "password", password))
                .retrieve()
                .toBodilessEntity();
    }
}
