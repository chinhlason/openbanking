package vn.com.truongsonbank.auth.authentication.infrastructure.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import vn.com.truongsonbank.auth.authentication.domain.AuthSession;
import vn.com.truongsonbank.auth.authentication.infrastructure.persistence.AuthDeviceRepository;
import vn.com.truongsonbank.shared.exception.UnauthorizedException;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.net.URI;

@Component
public class DpopProofVerifier {
    private static final String JTI_PREFIX = "tsb:auth:dpop:jti:";
    private static final String NONCE_PREFIX = "tsb:auth:dpop:nonce:";
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final AuthDeviceRepository deviceRepository;

    DpopProofVerifier(ObjectMapper objectMapper, StringRedisTemplate redis, AuthDeviceRepository deviceRepository) {
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.deviceRepository = deviceRepository;
    }

    public void verify(AuthSession session, String method, String requestUri, String proof) {
        DpopProof verified = verifyProof(method, requestUri, proof, null, false);
        if (session.dpopJkt() == null || !session.dpopJkt().equals(verified.jkt())) {
            throw new UnauthorizedException();
        }
    }

    public DpopProof verifyExchange(String method, String requestUri, String proof, String accessToken) {
        return verifyProof(method, requestUri, proof, accessToken, true);
    }

    public DpopProof verifyRequest(String method, String requestUri, String proof) {
        return verifyProof(method, requestUri, proof, null, false);
    }

    public String publicKeyThumbprint(String publicKeyBase64) {
        try {
            return jwkThumbprint(publicKey(publicKeyBase64));
        } catch (Exception ex) {
            throw new UnauthorizedException();
        }
    }

    public String newNonce() {
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        redis.opsForValue().set(NONCE_PREFIX + nonce, "1", 60, TimeUnit.SECONDS);
        return nonce;
    }

    private DpopProof verifyProof(String method, String requestUri, String proof, String accessToken, boolean requireNonce) {
        if (proof == null || proof.isBlank()) {
            throw new UnauthorizedException();
        }
        try {
            String[] parts = proof.split("\\.");
            if (parts.length != 3) {
                throw new UnauthorizedException();
            }
            JsonNode header = read(parts[0]);
            JsonNode payload = read(parts[1]);
            JsonNode jwk = header.path("jwk");
            if (!"ES256".equals(text(header, "alg")) || !"dpop+jwt".equalsIgnoreCase(text(header, "typ"))
                    || jwk.isMissingNode() || !method.equalsIgnoreCase(text(payload, "htm"))
                    || !matchesPath(text(payload, "htu"), requestUri)) {
                throw new UnauthorizedException();
            }
            long iat = payload.path("iat").asLong(0);
            if (Math.abs(Instant.now().getEpochSecond() - iat) > 300) {
                throw new UnauthorizedException();
            }
            String jti = text(payload, "jti");
            if (jti.isBlank() || Boolean.FALSE.equals(redis.opsForValue().setIfAbsent(JTI_PREFIX + jti, "1", 5, TimeUnit.MINUTES))) {
                throw new UnauthorizedException();
            }
            if (requireNonce) {
                String nonce = text(payload, "nonce");
                if (nonce.isBlank() || Boolean.FALSE.equals(redis.delete(NONCE_PREFIX + nonce))) {
                    throw new UnauthorizedException();
                }
            }
            if (accessToken != null && !ath(accessToken).equals(text(payload, "ath"))) {
                throw new UnauthorizedException();
            }
            ECPublicKey publicKey = jwkPublicKey(jwk);
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initVerify(publicKey);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(rawToDer(Base64.getUrlDecoder().decode(parts[2])))) {
                throw new UnauthorizedException();
            }
            return new DpopProof(jwkThumbprint(jwk), jti);
        } catch (Exception ex) {
            throw new UnauthorizedException();
        }
    }

    private boolean matchesPath(String htu, String requestUri) {
        try {
            String proofPath = URI.create(htu).getPath();
            String expected = v1Path(requestUri);
            return !expected.isBlank() && proofPath.endsWith(expected);
        } catch (Exception ex) {
            return false;
        }
    }

    private String v1Path(String path) {
        int index = path.indexOf("/v1/");
        return index < 0 ? path : path.substring(index);
    }

    private JsonNode read(String base64Url) throws java.io.IOException {
        return objectMapper.readTree(Base64.getUrlDecoder().decode(base64Url));
    }

    private String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    private ECPublicKey jwkPublicKey(JsonNode jwk) throws Exception {
        if (!"EC".equals(text(jwk, "kty")) || !"P-256".equals(text(jwk, "crv"))) {
            throw new UnauthorizedException();
        }
        byte[] x = Base64.getUrlDecoder().decode(text(jwk, "x"));
        byte[] y = Base64.getUrlDecoder().decode(text(jwk, "y"));
        ECPoint point = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new java.security.spec.ECPublicKeySpec(point, p256()));
    }

    private String jwkThumbprint(JsonNode jwk) throws Exception {
        String canonical = "{\"crv\":\"" + text(jwk, "crv") + "\",\"kty\":\"" + text(jwk, "kty") + "\",\"x\":\"" + text(jwk, "x") + "\",\"y\":\"" + text(jwk, "y") + "\"}";
        return base64Url(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private String jwkThumbprint(ECPublicKey publicKey) throws Exception {
        String x = base64Url(fixed32(publicKey.getW().getAffineX().toByteArray()));
        String y = base64Url(fixed32(publicKey.getW().getAffineY().toByteArray()));
        String canonical = "{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"" + x + "\",\"y\":\"" + y + "\"}";
        return base64Url(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private String ath(String accessToken) throws Exception {
        return base64Url(MessageDigest.getInstance("SHA-256").digest(accessToken.getBytes(StandardCharsets.US_ASCII)));
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

    private byte[] rawToDer(byte[] raw) {
        byte[] r = trim(Arrays.copyOfRange(raw, 0, 32));
        byte[] s = trim(Arrays.copyOfRange(raw, 32, 64));
        byte[] der = new byte[6 + r.length + s.length];
        der[0] = 0x30;
        der[1] = (byte) (4 + r.length + s.length);
        der[2] = 0x02;
        der[3] = (byte) r.length;
        System.arraycopy(r, 0, der, 4, r.length);
        der[4 + r.length] = 0x02;
        der[5 + r.length] = (byte) s.length;
        System.arraycopy(s, 0, der, 6 + r.length, s.length);
        return der;
    }

    private byte[] trim(byte[] value) {
        int offset = 0;
        while (offset < value.length - 1 && value[offset] == 0) {
            offset++;
        }
        byte[] trimmed = Arrays.copyOfRange(value, offset, value.length);
        if ((trimmed[0] & 0x80) == 0) {
            return trimmed;
        }
        byte[] positive = new byte[trimmed.length + 1];
        System.arraycopy(trimmed, 0, positive, 1, trimmed.length);
        return positive;
    }

    private byte[] fixed32(byte[] value) {
        byte[] trimmed = trim(value);
        if (trimmed.length == 32) {
            return trimmed;
        }
        if (trimmed.length > 32) {
            return Arrays.copyOfRange(trimmed, trimmed.length - 32, trimmed.length);
        }
        byte[] padded = new byte[32];
        System.arraycopy(trimmed, 0, padded, 32 - trimmed.length, trimmed.length);
        return padded;
    }

    public record DpopProof(String jkt, String jti) {
    }
}
