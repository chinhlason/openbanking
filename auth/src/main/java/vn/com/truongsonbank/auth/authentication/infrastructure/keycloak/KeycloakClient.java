package vn.com.truongsonbank.auth.authentication.infrastructure.keycloak;

import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;
import vn.com.truongsonbank.auth.authentication.config.AuthProperties;
import vn.com.truongsonbank.shared.exception.UnauthorizedException;

import java.util.List;
import java.util.Map;

@Component
public class KeycloakClient {
    private final RestClient restClient;
    private final AuthProperties properties;

    KeycloakClient(RestClient.Builder builder, AuthProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public String passwordGrant(String username, String pin) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", properties.getKeycloak().getMobileClientId());
        form.add("username", username);
        form.add("password", pin);

        try {
            KeycloakTokenResponse response = restClient.post()
                    .uri(properties.getKeycloak().getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KeycloakTokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new UnauthorizedException();
            }
            return response.accessToken();
        } catch (RuntimeException ex) {
            throw new UnauthorizedException();
        }
    }

    public String createUserWithPinIfAbsent(String username, String pin) {
        String token = adminToken();
        String userId = findUserId(username, token);
        if (userId == null) {
            userId = createUser(username, token);
        }
        setPassword(userId, pin, token);
        return userId;
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
                    .body(Map.of("username", username, "enabled", true))
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
}
