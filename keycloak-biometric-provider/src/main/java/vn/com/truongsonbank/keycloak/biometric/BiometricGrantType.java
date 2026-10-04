package vn.com.truongsonbank.keycloak.biometric;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.OAuth2Constants;
import org.keycloak.credential.CredentialModel;
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

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

public class BiometricGrantType extends OAuth2GrantTypeBase {
    @Override
    public Response process(Context context) {
        setContext(context);
        event.detail(Details.AUTH_METHOD, authMethod());

        if (!client.isDirectAccessGrantsEnabled()) {
            fail(OAuthErrorException.UNAUTHORIZED_CLIENT, "Client not allowed for " + authMethod() + " grant", Response.Status.BAD_REQUEST, Errors.NOT_ALLOWED);
        }
        if (client.isConsentRequired()) {
            fail(OAuthErrorException.INVALID_CLIENT, "Client requires user consent", Response.Status.BAD_REQUEST, Errors.CONSENT_DENIED);
        }

        String username = required("username");
        String deviceId = required("device_id");
        String challengeId = required("challenge_id");
        String nonce = required("nonce");
        String signature = required("signature");

        UserModel user = session.users().getUserByUsername(realm, username);
        if (user == null || !user.isEnabled()) {
            fail(OAuthErrorException.INVALID_GRANT, "Invalid biometric credential", Response.Status.BAD_REQUEST, Errors.INVALID_USER_CREDENTIALS);
        }
        verifySignature(user, deviceId, challengeId, nonce, signature);
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
                authMethod(),
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

    private void verifySignature(UserModel user, String deviceId, String challengeId, String nonce, String signature) {
        CredentialModel credential = BiometricCredentialResource.findCredential(user, deviceId, credentialType());
        String publicKey = BiometricCredentialResource.publicKey(credential);
        if (publicKey.isBlank()) {
            fail(OAuthErrorException.INVALID_GRANT, invalidCredentialMessage(), Response.Status.BAD_REQUEST, Errors.INVALID_USER_CREDENTIALS);
        }
        try {
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey(publicKey));
            verifier.update((challengeId + "." + nonce + "." + user.getUsername() + "." + deviceId).getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(Base64.getDecoder().decode(signature))) {
                fail(OAuthErrorException.INVALID_GRANT, invalidCredentialMessage(), Response.Status.BAD_REQUEST, Errors.INVALID_USER_CREDENTIALS);
            }
        } catch (CorsErrorResponseException e) {
            throw e;
        } catch (Exception e) {
            fail(OAuthErrorException.INVALID_GRANT, invalidCredentialMessage(), Response.Status.BAD_REQUEST, Errors.INVALID_USER_CREDENTIALS);
        }
    }

    protected String authMethod() {
        return "biometric";
    }

    protected String credentialType() {
        return BiometricCredentialResource.BIOMETRIC_TYPE;
    }

    protected String invalidCredentialMessage() {
        return "Invalid biometric credential";
    }

    private ECPublicKey publicKey(String encoded) throws Exception {
        byte[] key = Base64.getDecoder().decode(encoded);
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(key));
        } catch (Exception ignored) {
            if (key.length != 65 || key[0] != 0x04) {
                throw ignored;
            }
            ECParameterSpec params = p256();
            ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(key, 1, 33)),
                    new BigInteger(1, Arrays.copyOfRange(key, 33, 65)));
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, params));
        }
    }

    private ECParameterSpec p256() {
        BigInteger p = new BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF", 16);
        BigInteger a = new BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC", 16);
        BigInteger b = new BigInteger("5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B", 16);
        BigInteger gx = new BigInteger("6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296", 16);
        BigInteger gy = new BigInteger("4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5", 16);
        BigInteger n = new BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16);
        return new ECParameterSpec(new EllipticCurve(new ECFieldFp(p), a, b), new ECPoint(gx, gy), n, 1);
    }

    private void fail(String error, String message, Response.Status status, String eventError) {
        event.detail(Details.REASON, message);
        event.error(eventError);
        throw new CorsErrorResponseException(cors, error, message, status);
    }
}
