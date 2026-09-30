package vn.com.truongsonbank.auth.login;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import vn.com.truongsonbank.shared.exception.UnauthorizedException;

@Component
class KeycloakClient {
    private final RestClient restClient;
    private final AuthProperties properties;

    KeycloakClient(RestClient.Builder builder, AuthProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    String passwordGrant(String username, String pin) {
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
}
