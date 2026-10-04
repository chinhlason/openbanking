package vn.com.truongsonbank.auth.authentication.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import vn.com.truongsonbank.auth.authentication.adapter.in.web.DeviceRequest;
import vn.com.truongsonbank.auth.authentication.adapter.in.web.LoginChallengeResponse;
import vn.com.truongsonbank.auth.authentication.adapter.in.web.LoginInitRequest;
import vn.com.truongsonbank.auth.authentication.adapter.in.web.LoginVerifyRequest;
import vn.com.truongsonbank.auth.authentication.adapter.in.web.SessionResponse;
import vn.com.truongsonbank.auth.authentication.domain.AuthErrors;
import vn.com.truongsonbank.auth.authentication.domain.LoginChallengeState;
import vn.com.truongsonbank.auth.authentication.infrastructure.keycloak.KeycloakClient;
import vn.com.truongsonbank.auth.authentication.infrastructure.keycloak.KeycloakTokenValidator;
import vn.com.truongsonbank.auth.authentication.infrastructure.security.DpopProofVerifier;
import vn.com.truongsonbank.shared.exception.BusinessException;
import vn.com.truongsonbank.shared.exception.UnauthorizedException;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class LoginChallengeService {
    private static final String PREFIX = "tsb:auth:login:challenge:";
    private static final Duration TTL = Duration.ofMinutes(2);

    private final SecureRandom random = new SecureRandom();
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final DpopProofVerifier dpopProofVerifier;
    private final KeycloakClient keycloakClient;
    private final KeycloakTokenValidator tokenValidator;
    private final AuthSessionService sessionService;

    LoginChallengeService(StringRedisTemplate redis,
                          ObjectMapper objectMapper,
                          DpopProofVerifier dpopProofVerifier,
                          KeycloakClient keycloakClient,
                          KeycloakTokenValidator tokenValidator,
                          AuthSessionService sessionService) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.dpopProofVerifier = dpopProofVerifier;
        this.keycloakClient = keycloakClient;
        this.tokenValidator = tokenValidator;
        this.sessionService = sessionService;
    }

    public LoginChallengeResponse init(LoginInitRequest request) {
        String loginType = loginType(request == null ? null : request.loginType());
        String username = trim(request == null ? null : request.username());
        DeviceRequest device = request == null ? null : request.device();
        if (isBlank(username) || device == null || isBlank(device.deviceId()) || isBlank(device.publicKey())) {
            throw error(AuthErrors.LOGIN_REQUEST_INVALID);
        }
        String dpopJkt;
        try {
            dpopJkt = dpopProofVerifier.publicKeyThumbprint(device.publicKey());
        } catch (UnauthorizedException e) {
            throw error(AuthErrors.DPOP_INVALID);
        }
        String challengeId = "login_" + token(18);
        String nonce = token(24);
        LoginChallengeState state = new LoginChallengeState(loginType, username, device.deviceId(), nonce, dpopJkt);
        try {
            redis.opsForValue().set(PREFIX + challengeId, objectMapper.writeValueAsString(state), TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return new LoginChallengeResponse(
                challengeId,
                nonce,
                TTL.toSeconds(),
                payload(loginType, challengeId, nonce, username, device.deviceId(), dpopJkt),
                List.of("PIN", "PASSWORD", "BIOMETRIC", "PASSKEY"),
                true
        );
    }

    public SessionResponse verify(LoginVerifyRequest request, String proof, HttpServletRequest httpRequest) {
        if (request == null || isBlank(request.challengeId()) || isBlank(request.nonce())) {
            throw error(AuthErrors.LOGIN_REQUEST_INVALID);
        }
        String key = PREFIX + request.challengeId();
        String json = redis.opsForValue().get(key);
        if (json == null) {
            throw error(AuthErrors.LOGIN_CHALLENGE_EXPIRED);
        }
        try {
            LoginChallengeState state = objectMapper.readValue(json, LoginChallengeState.class);
            if (!externalCredential(state.loginType())) {
                redis.delete(key);
            }
            DeviceRequest device = request.device();
            if (device == null
                    || !state.loginType().equals(loginType(request.loginType()))
                    || !state.username().equals(trim(request.username()))
                    || !state.deviceId().equals(device.deviceId())
                    || !state.nonce().equals(request.nonce())) {
                throw error(AuthErrors.LOGIN_CHALLENGE_MISMATCH);
            }
            DpopProofVerifier.DpopProof dpop;
            try {
                dpop = dpopProofVerifier.verifyRequest(httpRequest.getMethod(), httpRequest.getRequestURI(), proof);
            } catch (UnauthorizedException e) {
                throw error(AuthErrors.DPOP_INVALID);
            }
            if (!state.dpopJkt().equals(dpop.jkt())) {
                throw error(AuthErrors.DPOP_MISMATCH);
            }
            String accessToken = accessToken(state, request);
            Jwt jwt = tokenValidator.validate(accessToken);
            return sessionService.create(jwt, device, dpop.jkt());
        } catch (JsonProcessingException e) {
            throw error(AuthErrors.LOGIN_CHALLENGE_MISMATCH);
        }
    }

    private String credential(LoginVerifyRequest request) {
        String type = loginType(request.loginType());
        String value = "PASSWORD".equals(type) ? request.password() : request.pin();
        if (isBlank(value)) {
            throw error(AuthErrors.LOGIN_REQUEST_INVALID);
        }
        return value;
    }

    private String accessToken(LoginChallengeState state, LoginVerifyRequest request) {
        String type = loginType(request.loginType());
        if ("BIOMETRIC".equals(type)) {
            if (isBlank(request.signature())) {
                throw error(AuthErrors.LOGIN_SIGNATURE_REQUIRED);
            }
            return keycloakClient.biometricGrant(state.username(), state.deviceId(), request.challengeId(), request.nonce(), request.signature(), request.keycloakDpopProof());
        }
        if ("PASSKEY".equals(type)) {
            if (isBlank(request.signature())) {
                throw error(AuthErrors.LOGIN_SIGNATURE_REQUIRED);
            }
            return keycloakClient.passkeyGrant(state.username(), state.deviceId(), request.challengeId(), request.nonce(), request.signature(), request.keycloakDpopProof());
        }
        if ("PIN".equals(type)) {
            return keycloakClient.pinGrant(state.username(), credential(request), request.keycloakDpopProof());
        }
        return keycloakClient.passwordGrant(state.username(), credential(request), request.keycloakDpopProof());
    }

    private boolean externalCredential(String loginType) {
        return "BIOMETRIC".equals(loginType) || "PASSKEY".equals(loginType);
    }

    private String loginType(String value) {
        String normalized = trim(value).toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "PIN";
        }
        if (!"PIN".equals(normalized) && !"PASSWORD".equals(normalized)
                && !"BIOMETRIC".equals(normalized) && !"PASSKEY".equals(normalized)) {
            throw error(AuthErrors.LOGIN_TYPE_UNSUPPORTED);
        }
        return normalized;
    }

    private String payload(String loginType, String challengeId, String nonce, String username, String deviceId, String dpopJkt) {
        if (externalCredential(loginType)) {
            return challengeId + "." + nonce + "." + username + "." + deviceId;
        }
        return "LOGIN." + loginType + "." + challengeId + "." + nonce + "." + username + "." + deviceId + "." + dpopJkt;
    }

    private BusinessException error(AuthErrors error) {
        return new BusinessException(error);
    }

    private String token(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
