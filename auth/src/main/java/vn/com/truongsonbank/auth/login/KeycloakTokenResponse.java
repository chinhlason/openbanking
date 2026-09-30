package vn.com.truongsonbank.auth.login;

import com.fasterxml.jackson.annotation.JsonProperty;

record KeycloakTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("token_type") String tokenType
) {
}
