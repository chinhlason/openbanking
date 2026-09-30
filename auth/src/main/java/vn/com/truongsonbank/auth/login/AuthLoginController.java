package vn.com.truongsonbank.auth.login;

import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.truongsonbank.shared.exception.UnauthorizedException;
import vn.com.truongsonbank.shared.response.ResponseWrapper;

import java.util.Map;

@RestController
@RequestMapping("/v1")
@ResponseWrapper
class AuthLoginController {
    private final KeycloakClient keycloakClient;
    private final KeycloakTokenValidator tokenValidator;
    private final AuthSessionService sessionService;
    private final DpopProofVerifier dpopProofVerifier;
    private final BiometricAuthService biometricAuthService;
    private final AuthProperties authProperties;

    AuthLoginController(KeycloakClient keycloakClient,
                        KeycloakTokenValidator tokenValidator,
                        AuthSessionService sessionService,
                        DpopProofVerifier dpopProofVerifier,
                        BiometricAuthService biometricAuthService,
                        AuthProperties authProperties) {
        this.keycloakClient = keycloakClient;
        this.tokenValidator = tokenValidator;
        this.sessionService = sessionService;
        this.dpopProofVerifier = dpopProofVerifier;
        this.biometricAuthService = biometricAuthService;
        this.authProperties = authProperties;
    }

    @PostMapping("/login/pin")
    SessionResponse login(@RequestHeader("DPoP") String proof,
                          @RequestBody LoginRequest request,
                          HttpServletRequest httpRequest) {
        throw new UnauthorizedException();
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
        keycloakClient.passwordGrant(session.username(), trustRequest.pin());
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

    @PostMapping("/biometric/challenge")
    BiometricChallengeResponse biometricChallenge(@RequestBody BiometricChallengeRequest request) {
        return biometricAuthService.challenge(request);
    }

    @PostMapping("/internal/biometric/verify")
    BiometricVerifyResponse verifyBiometric(@RequestHeader("X-Keycloak-Biometric-Secret") String secret,
                                            @RequestBody BiometricVerifyRequest request) {
        if (!authProperties.getBiometricGrantSecret().equals(secret)) {
            throw new UnauthorizedException();
        }
        return biometricAuthService.verify(request);
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
}
