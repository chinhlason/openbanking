package vn.com.truongsonbank.auth.authentication.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import vn.com.truongsonbank.auth.authentication.adapter.in.web.BiometricChallengeResponse;
import vn.com.truongsonbank.auth.authentication.adapter.in.web.BiometricEnableRequest;
import vn.com.truongsonbank.auth.authentication.domain.AuthSession;
import vn.com.truongsonbank.auth.authentication.domain.BiometricEnableChallengeState;
import vn.com.truongsonbank.auth.authentication.infrastructure.keycloak.KeycloakClient;
import vn.com.truongsonbank.auth.authentication.infrastructure.persistence.AuthDeviceEntity;
import vn.com.truongsonbank.auth.authentication.infrastructure.persistence.AuthDeviceRepository;
import vn.com.truongsonbank.auth.authentication.infrastructure.security.DpopProofVerifier;
import vn.com.truongsonbank.shared.exception.UnauthorizedException;

import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

@Service
public class BiometricAuthService {
    private static final String ENABLE_PREFIX = "tsb:auth:biometric:enable:";
    private static final String PASSKEY_ENABLE_PREFIX = "tsb:auth:passkey:enable:";
    private static final Duration TTL = Duration.ofSeconds(60);
    private final SecureRandom random = new SecureRandom();
    private final AuthDeviceRepository deviceRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final DpopProofVerifier dpopProofVerifier;
    private final KeycloakClient keycloakClient;

    BiometricAuthService(AuthDeviceRepository deviceRepository,
                         StringRedisTemplate redis,
                         ObjectMapper objectMapper,
                         DpopProofVerifier dpopProofVerifier,
                         KeycloakClient keycloakClient) {
        this.deviceRepository = deviceRepository;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.dpopProofVerifier = dpopProofVerifier;
        this.keycloakClient = keycloakClient;
    }

    public BiometricChallengeResponse enableChallenge(AuthSession session, String deviceId) {
        trustedSessionDevice(session, deviceId);
        String challengeId = "bio_enable_" + token(18);
        String nonce = token(24);
        try {
            redis.opsForValue().set(ENABLE_PREFIX + challengeId,
                    objectMapper.writeValueAsString(new BiometricEnableChallengeState(session.sessionId(), session.username(), deviceId, nonce, session.dpopJkt())),
                    TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return new BiometricChallengeResponse(challengeId, nonce, TTL.toSeconds(), enablePayload(challengeId, nonce, session.username(), deviceId));
    }

    public Map<String, Object> enable(AuthSession session, String deviceId, BiometricEnableRequest request) {
        AuthDeviceEntity device = trustedSessionDevice(session, deviceId);
        if (request == null || isBlank(request.publicKey()) || isBlank(request.challengeId()) || isBlank(request.nonce()) || isBlank(request.signature())) {
            throw new UnauthorizedException();
        }
        String key = ENABLE_PREFIX + request.challengeId();
        String json = redis.opsForValue().get(key);
        redis.delete(key);
        if (json == null) {
            throw new UnauthorizedException();
        }
        try {
            BiometricEnableChallengeState state = objectMapper.readValue(json, BiometricEnableChallengeState.class);
            if (!session.sessionId().equals(state.sessionId())
                    || !session.username().equals(state.username())
                    || !deviceId.equals(state.deviceId())
                    || !request.nonce().equals(state.nonce())
                    || !session.dpopJkt().equals(state.dpopJkt())) {
                throw new UnauthorizedException();
            }
            verifySignature(request.publicKey(), enablePayload(request.challengeId(), request.nonce(), session.username(), deviceId), request.signature());
        } catch (JsonProcessingException e) {
            throw new UnauthorizedException();
        }
        device.setBiometricEnabled(true);
        deviceRepository.save(device);
        keycloakClient.enableBiometricCredential(session.username(), deviceId, request.publicKey());
        return Map.of("deviceId", deviceId, "biometricEnabled", true);
    }

    public Map<String, Object> disable(AuthSession session, String deviceId) {
        AuthDeviceEntity device = trustedSessionDevice(session, deviceId);
        device.setBiometricEnabled(false);
        deviceRepository.save(device);
        keycloakClient.disableBiometricCredential(session.username(), deviceId);
        return Map.of("deviceId", deviceId, "biometricEnabled", false);
    }

    public BiometricChallengeResponse passkeyEnableChallenge(AuthSession session, String deviceId) {
        trustedSessionDevice(session, deviceId);
        String challengeId = "passkey_enable_" + token(18);
        String nonce = token(24);
        try {
            redis.opsForValue().set(PASSKEY_ENABLE_PREFIX + challengeId,
                    objectMapper.writeValueAsString(new BiometricEnableChallengeState(session.sessionId(), session.username(), deviceId, nonce, session.dpopJkt())),
                    TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return new BiometricChallengeResponse(challengeId, nonce, TTL.toSeconds(), passkeyEnablePayload(challengeId, nonce, session.username(), deviceId));
    }

    public Map<String, Object> enablePasskey(AuthSession session, String deviceId, BiometricEnableRequest request) {
        AuthDeviceEntity device = trustedSessionDevice(session, deviceId);
        if (request == null || isBlank(request.publicKey()) || isBlank(request.challengeId()) || isBlank(request.nonce()) || isBlank(request.signature())) {
            throw new UnauthorizedException();
        }
        String key = PASSKEY_ENABLE_PREFIX + request.challengeId();
        String json = redis.opsForValue().get(key);
        redis.delete(key);
        if (json == null) {
            throw new UnauthorizedException();
        }
        try {
            BiometricEnableChallengeState state = objectMapper.readValue(json, BiometricEnableChallengeState.class);
            if (!session.sessionId().equals(state.sessionId())
                    || !session.username().equals(state.username())
                    || !deviceId.equals(state.deviceId())
                    || !request.nonce().equals(state.nonce())
                    || !session.dpopJkt().equals(state.dpopJkt())) {
                throw new UnauthorizedException();
            }
            verifySignature(request.publicKey(), passkeyEnablePayload(request.challengeId(), request.nonce(), session.username(), deviceId), request.signature());
        } catch (JsonProcessingException e) {
            throw new UnauthorizedException();
        }
        device.setPasskeyEnabled(true);
        deviceRepository.save(device);
        keycloakClient.enablePasskeyCredential(session.username(), deviceId, request.publicKey());
        return Map.of("deviceId", deviceId, "passkeyEnabled", true);
    }

    public Map<String, Object> disablePasskey(AuthSession session, String deviceId) {
        AuthDeviceEntity device = trustedSessionDevice(session, deviceId);
        device.setPasskeyEnabled(false);
        deviceRepository.save(device);
        keycloakClient.disablePasskeyCredential(session.username(), deviceId);
        return Map.of("deviceId", deviceId, "passkeyEnabled", false);
    }

    private AuthDeviceEntity trustedSessionDevice(AuthSession session, String deviceId) {
        if (!session.deviceId().equals(deviceId) || !session.trustedDevice()) {
            throw new UnauthorizedException();
        }
        AuthDeviceEntity device = deviceRepository.findById(deviceId).orElseThrow(UnauthorizedException::new);
        if (!session.subject().equals(device.getSubject()) || !device.isTrusted()
                || isBlank(device.getPublicKey())
                || isBlank(session.dpopJkt())
                || !session.dpopJkt().equals(dpopProofVerifier.publicKeyThumbprint(device.getPublicKey()))) {
            throw new UnauthorizedException();
        }
        return device;
    }

    private void verifySignature(String publicKeyBase64, String payload, String signatureBase64) {
        try {
            ECPublicKey publicKey = publicKey(publicKeyBase64);
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!verifier.verify(Base64.getDecoder().decode(signatureBase64))) {
                throw new UnauthorizedException();
            }
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException();
        }
    }

    private ECPublicKey publicKey(String encoded) throws Exception {
        byte[] key = Base64.getDecoder().decode(encoded);
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(key));
        } catch (Exception ignored) {
            return x963PublicKey(key);
        }
    }

    private ECPublicKey x963PublicKey(byte[] key) throws Exception {
        if (key.length != 65 || key[0] != 0x04) {
            throw new UnauthorizedException();
        }
        ECParameterSpec params = p256();
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(key, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(key, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new java.security.spec.ECPublicKeySpec(point, params));
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

    private String payload(String challengeId, String nonce, String username, String deviceId) {
        return challengeId + "." + nonce + "." + username + "." + deviceId;
    }

    private String enablePayload(String challengeId, String nonce, String username, String deviceId) {
        return "enable." + payload(challengeId, nonce, username, deviceId);
    }

    private String passkeyEnablePayload(String challengeId, String nonce, String username, String deviceId) {
        return "passkey.enable." + payload(challengeId, nonce, username, deviceId);
    }

    private String token(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
