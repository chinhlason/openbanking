package vn.com.truongsonbank.auth.authentication.adapter.in.web;

import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.truongsonbank.auth.authentication.application.AuthSessionService;
import vn.com.truongsonbank.auth.authentication.application.BiometricAuthService;
import vn.com.truongsonbank.auth.authentication.application.LoginChallengeService;
import vn.com.truongsonbank.auth.authentication.config.AuthProperties;
import vn.com.truongsonbank.auth.authentication.domain.AuthSession;
import vn.com.truongsonbank.auth.authentication.infrastructure.keycloak.KeycloakClient;
import vn.com.truongsonbank.auth.authentication.infrastructure.keycloak.KeycloakTokenValidator;
import vn.com.truongsonbank.auth.authentication.infrastructure.security.DpopProofVerifier;
import vn.com.truongsonbank.shared.exception.UnauthorizedException;
import vn.com.truongsonbank.shared.response.ResponseWrapper;

import java.util.Map;

@RestController
@RequestMapping("/v1")
@ResponseWrapper
public class AuthLoginController {
    private final KeycloakClient keycloakClient;
    private final KeycloakTokenValidator tokenValidator;
    private final AuthSessionService sessionService;
    private final DpopProofVerifier dpopProofVerifier;
    private final BiometricAuthService biometricAuthService;
    private final LoginChallengeService loginChallengeService;
    private final AuthProperties authProperties;

    AuthLoginController(KeycloakClient keycloakClient,
                        KeycloakTokenValidator tokenValidator,
                        AuthSessionService sessionService,
                        DpopProofVerifier dpopProofVerifier,
                        BiometricAuthService biometricAuthService,
                        LoginChallengeService loginChallengeService,
                        AuthProperties authProperties) {
        this.keycloakClient = keycloakClient;
        this.tokenValidator = tokenValidator;
        this.sessionService = sessionService;
        this.dpopProofVerifier = dpopProofVerifier;
        this.biometricAuthService = biometricAuthService;
        this.loginChallengeService = loginChallengeService;
        this.authProperties = authProperties;
    }

    @PostMapping("/login/init")
    LoginChallengeResponse loginInit(@RequestBody LoginInitRequest request) {
        return loginChallengeService.init(request);
    }

    @PostMapping("/login/verify")
    SessionResponse loginVerify(@RequestHeader("DPoP") String proof,
                                @RequestBody LoginVerifyRequest request,
                                HttpServletRequest httpRequest) {
        return loginChallengeService.verify(request, proof, httpRequest);
    }

    @PostMapping("/token/exchange")
    SessionResponse exchange(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                             @RequestHeader("DPoP") String proof,
                             @RequestBody(required = false) ExchangeRequest request,
                             HttpServletRequest httpRequest) {
        String accessToken = accessToken(authorization);
        DpopProofVerifier.DpopProof dpop = dpopProofVerifier.verifyExchange(httpRequest.getMethod(), httpRequest.getRequestURI(), proof, accessToken);
        Jwt jwt = tokenValidator.validate(accessToken);
        if (!dpop.jkt().equals(cnfJkt(jwt))) {
            throw new UnauthorizedException();
        }
        return sessionService.create(jwt,
                request == null ? null : request.device(),
                dpop.jkt());
    }

    @PostMapping("/dpop/nonce")
    DpopNonceResponse dpopNonce() {
        return new DpopNonceResponse(dpopProofVerifier.newNonce(), 60);
    }

    @PostMapping("/sessions/keep-alive")
    SessionResponse keepAlive(@RequestHeader("X-Session-Id") String sessionId,
                              @RequestHeader("DPoP") String proof,
                              HttpServletRequest request) {
        dpopProofVerifier.verify(sessionService.read(sessionId), request.getMethod(), request.getRequestURI(), proof);
        return sessionService.keepAlive(sessionId);
    }

    @GetMapping("/sessions/me")
    AuthSession me(@RequestHeader("X-Session-Id") String sessionId,
                   @RequestHeader("DPoP") String proof,
                   HttpServletRequest request) {
        AuthSession session = sessionService.read(sessionId);
        dpopProofVerifier.verify(session, request.getMethod(), request.getRequestURI(), proof);
        return session;
    }

    @GetMapping("/devices")
    java.util.List<DeviceResponse> devices(@RequestHeader("X-Session-Id") String sessionId,
                                           @RequestHeader("DPoP") String proof,
                                           HttpServletRequest request) {
        dpopProofVerifier.verify(sessionService.read(sessionId), request.getMethod(), request.getRequestURI(), proof);
        return sessionService.devices(sessionId);
    }

    @GetMapping("/sessions")
    java.util.List<SessionViewResponse> sessions(@RequestHeader("X-Session-Id") String sessionId,
                                                 @RequestHeader("DPoP") String proof,
                                                 HttpServletRequest request) {
        dpopProofVerifier.verify(sessionService.read(sessionId), request.getMethod(), request.getRequestURI(), proof);
        return sessionService.sessions(sessionId);
    }

    @PostMapping("/devices/{deviceId}/trust")
    SessionResponse trustDevice(@RequestHeader("X-Session-Id") String sessionId,
                                @RequestHeader("DPoP") String proof,
                                @org.springframework.web.bind.annotation.PathVariable String deviceId,
                                @RequestBody TrustDeviceRequest trustRequest,
                                HttpServletRequest request) {
        AuthSession session = sessionService.read(sessionId);
        dpopProofVerifier.verify(session, request.getMethod(), request.getRequestURI(), proof);
        keycloakClient.pinGrant(session.username(), trustRequest.pin(), trustRequest.keycloakDpopProof());
        return sessionService.trustDevice(session, deviceId);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/sessions/{sessionId}")
    Map<String, Object> revoke(@RequestHeader("X-Session-Id") String currentSessionId,
                               @RequestHeader("DPoP") String proof,
                               @org.springframework.web.bind.annotation.PathVariable String sessionId,
                               HttpServletRequest request) {
        dpopProofVerifier.verify(sessionService.read(currentSessionId), request.getMethod(), request.getRequestURI(), proof);
        sessionService.revoke(currentSessionId, sessionId);
        return Map.of("revoked", true);
    }

    @PostMapping("/devices/{deviceId}/biometric/enable")
    Map<String, Object> enableBiometric(@RequestHeader("X-Session-Id") String sessionId,
                                        @RequestHeader("DPoP") String proof,
                                        @org.springframework.web.bind.annotation.PathVariable String deviceId,
                                        @RequestBody BiometricEnableRequest enableRequest,
                                        HttpServletRequest request) {
        AuthSession session = sessionService.read(sessionId);
        dpopProofVerifier.verify(session, request.getMethod(), request.getRequestURI(), proof);
        return biometricAuthService.enable(session, deviceId, enableRequest);
    }

    @PostMapping("/devices/{deviceId}/biometric/enable/challenge")
    BiometricChallengeResponse biometricEnableChallenge(@RequestHeader("X-Session-Id") String sessionId,
                                                        @RequestHeader("DPoP") String proof,
                                                        @org.springframework.web.bind.annotation.PathVariable String deviceId,
                                                        HttpServletRequest request) {
        AuthSession session = sessionService.read(sessionId);
        dpopProofVerifier.verify(session, request.getMethod(), request.getRequestURI(), proof);
        return biometricAuthService.enableChallenge(session, deviceId);
    }

    @PostMapping("/devices/{deviceId}/biometric/disable")
    Map<String, Object> disableBiometric(@RequestHeader("X-Session-Id") String sessionId,
                                         @RequestHeader("DPoP") String proof,
                                         @org.springframework.web.bind.annotation.PathVariable String deviceId,
                                         HttpServletRequest request) {
        AuthSession session = sessionService.read(sessionId);
        dpopProofVerifier.verify(session, request.getMethod(), request.getRequestURI(), proof);
        return biometricAuthService.disable(session, deviceId);
    }

    @PostMapping("/devices/{deviceId}/passkey/enable")
    Map<String, Object> enablePasskey(@RequestHeader("X-Session-Id") String sessionId,
                                      @RequestHeader("DPoP") String proof,
                                      @org.springframework.web.bind.annotation.PathVariable String deviceId,
                                      @RequestBody BiometricEnableRequest enableRequest,
                                      HttpServletRequest request) {
        AuthSession session = sessionService.read(sessionId);
        dpopProofVerifier.verify(session, request.getMethod(), request.getRequestURI(), proof);
        return biometricAuthService.enablePasskey(session, deviceId, enableRequest);
    }

    @PostMapping("/devices/{deviceId}/passkey/enable/challenge")
    BiometricChallengeResponse passkeyEnableChallenge(@RequestHeader("X-Session-Id") String sessionId,
                                                     @RequestHeader("DPoP") String proof,
                                                     @org.springframework.web.bind.annotation.PathVariable String deviceId,
                                                     HttpServletRequest request) {
        AuthSession session = sessionService.read(sessionId);
        dpopProofVerifier.verify(session, request.getMethod(), request.getRequestURI(), proof);
        return biometricAuthService.passkeyEnableChallenge(session, deviceId);
    }

    @PostMapping("/devices/{deviceId}/passkey/disable")
    Map<String, Object> disablePasskey(@RequestHeader("X-Session-Id") String sessionId,
                                      @RequestHeader("DPoP") String proof,
                                      @org.springframework.web.bind.annotation.PathVariable String deviceId,
                                      HttpServletRequest request) {
        AuthSession session = sessionService.read(sessionId);
        dpopProofVerifier.verify(session, request.getMethod(), request.getRequestURI(), proof);
        return biometricAuthService.disablePasskey(session, deviceId);
    }

    @PostMapping("/internal/onboarding/complete")
    SessionResponse completeOnboarding(@RequestHeader("X-Internal-Onboarding-Secret") String secret,
                                       @RequestHeader("DPoP") String proof,
                                       @RequestBody OnboardingCompleteRequest request) {
        if (!authProperties.getOnboardingInternalSecret().equals(secret)) {
            throw new UnauthorizedException();
        }
        DpopProofVerifier.DpopProof dpop = dpopProofVerifier.verifyRequest(request.dpopHtm(), request.dpopHtu(), proof);
        String subject = keycloakClient.createUserWithPinIfAbsent(request.username(), request.pin());
        return sessionService.createTrusted(subject, request.username(), request.device(), dpop.jkt());
    }

    @PostMapping("/internal/sessions/introspect")
    AuthSession introspect(@RequestHeader("X-Internal-Onboarding-Secret") String secret,
                           @RequestBody SessionIntrospectRequest request) {
        if (!authProperties.getOnboardingInternalSecret().equals(secret) || request == null) {
            throw new UnauthorizedException();
        }
        return sessionService.read(request.sessionId());
    }

    @PostMapping("/internal/sessions/{sessionId}/customer")
    SessionResponse bindCustomer(@RequestHeader("X-Internal-Onboarding-Secret") String secret,
                                 @org.springframework.web.bind.annotation.PathVariable String sessionId,
                                 @RequestBody CustomerBindingRequest request) {
        if (!authProperties.getOnboardingInternalSecret().equals(secret) || request == null) {
            throw new UnauthorizedException();
        }
        return sessionService.bindCustomer(sessionId, request.customerId());
    }

    @PostMapping("/keycloak/token/validate")
    Map<String, Object> validate(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        Jwt jwt = tokenValidator.validate(accessToken(authorization));
        return Map.of(
                "sub", jwt.getSubject(),
                "username", jwt.getClaimAsString("preferred_username"),
                "azp", jwt.getClaimAsString("azp"),
                "issuer", jwt.getIssuer().toString(),
                "expiresAt", jwt.getExpiresAt()
        );
    }

    private String accessToken(String authorization) {
        if (authorization == null) {
            throw new UnauthorizedException();
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        if (authorization.startsWith("DPoP ")) {
            return authorization.substring(5);
        }
        throw new UnauthorizedException();
    }

    @SuppressWarnings("unchecked")
    private String cnfJkt(Jwt jwt) {
        Object cnf = jwt.getClaim("cnf");
        if (cnf instanceof Map<?, ?> map) {
            Object jkt = map.get("jkt");
            return jkt instanceof String value ? value : "";
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CustomerBindingRequest(String customerId) { }
}
