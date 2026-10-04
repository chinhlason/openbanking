package vn.com.truongsonbank.keycloak.biometric;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.OAuth2Constants;
import org.keycloak.OAuthErrorException;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventType;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.TokenManager;
import org.keycloak.protocol.oidc.grants.OAuth2GrantTypeBase;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.services.CorsErrorResponseException;
import org.keycloak.services.Urls;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.managers.AuthenticationSessionManager;
import org.keycloak.services.managers.UserSessionManager;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import org.keycloak.util.TokenUtil;

public class TsbPinGrantType extends OAuth2GrantTypeBase {
    @Override
    public Response process(Context context) {
        setContext(context);
        event.detail(Details.AUTH_METHOD, "tsb-pin");

        if (!client.isDirectAccessGrantsEnabled()) {
            fail(OAuthErrorException.UNAUTHORIZED_CLIENT, "Client not allowed for tsb-pin grant", Response.Status.BAD_REQUEST, Errors.NOT_ALLOWED);
        }
        if (client.isConsentRequired()) {
            fail(OAuthErrorException.INVALID_CLIENT, "Client requires user consent", Response.Status.BAD_REQUEST, Errors.CONSENT_DENIED);
        }

        String username = required("username");
        String pin = required("pin");
        UserModel user = session.users().getUserByUsername(realm, username);
        if (user == null || !user.isEnabled() || !BiometricCredentialResource.verifyPin(user, pin)) {
            fail(OAuthErrorException.INVALID_GRANT, "Invalid PIN credential", Response.Status.BAD_REQUEST, Errors.INVALID_USER_CREDENTIALS);
        }

        event.detail(Details.USERNAME, username);
        event.user(user);

        String scope = getRequestedScopes();
        RootAuthenticationSessionModel rootAuthSession = new AuthenticationSessionManager(session).createAuthenticationSession(realm, false);
        AuthenticationSessionModel authSession = rootAuthSession.createAuthenticationSession(client);
        authSession.setAuthenticatedUser(user);
        authSession.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        authSession.setClientNote(OIDCLoginProtocol.ISSUER, Urls.realmIssuer(session.getContext().getUri().getBaseUri(), realm.getName()));
        authSession.setClientNote(OIDCLoginProtocol.SCOPE_PARAM, scope);

        UserSessionModel userSession = new UserSessionManager(session).createUserSession(
                authSession.getParentSession().getId(),
                realm,
                user,
                username,
                clientConnection.getRemoteAddr(),
                "tsb-pin",
                false,
                null,
                null,
                UserSessionModel.SessionPersistenceState.PERSISTENT);
        event.session(userSession);

        AuthenticationManager.setClientScopesInSession(session, authSession);
        ClientSessionContext clientSessionCtx = TokenManager.attachAuthenticationSession(session, userSession, authSession);
        updateUserSessionFromClientAuth(userSession);

        TokenManager.AccessTokenResponseBuilder responseBuilder = tokenManager
                .responseBuilder(realm, client, event, session, userSession, clientSessionCtx)
                .generateAccessToken();
        boolean useRefreshToken = clientConfig.isUseRefreshToken();
        if (useRefreshToken) {
            responseBuilder.generateRefreshToken();
        }
        checkAndBindMtlsHoKToken(responseBuilder, useRefreshToken);

        String scopeParam = clientSessionCtx.getClientSession().getNote(OAuth2Constants.SCOPE);
        if (TokenUtil.isOIDCRequest(scopeParam)) {
            responseBuilder.generateIDToken().generateAccessTokenHash();
        }

        AccessTokenResponse res = responseBuilder.build();
        event.success();
        return cors.add(Response.ok(res, MediaType.APPLICATION_JSON_TYPE));
    }

    @Override
    public EventType getEventType() {
        return EventType.LOGIN;
    }

    private String required(String name) {
        String value = formParams.getFirst(name);
        if (value == null || value.isBlank()) {
            fail(OAuthErrorException.INVALID_REQUEST, "Missing " + name, Response.Status.BAD_REQUEST, Errors.INVALID_REQUEST);
        }
        return value;
    }

    private void fail(String error, String message, Response.Status status, String eventError) {
        event.detail(Details.REASON, message);
        event.error(eventError);
        throw new CorsErrorResponseException(cors, error, message, status);
    }
}
