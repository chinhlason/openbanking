package vn.com.truongsonbank.shared.crypto;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

final class DefaultTsbCryptoService implements TsbCryptoService {
    private static final String ENCRYPTED_PREFIX = "ENC:v1:";
    private static final String HASH_PREFIX = "HMAC:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String keyId;
    private final byte[] encryptionKey;
    private final byte[] hashKey;
    private final SecureRandom secureRandom = new SecureRandom();

    DefaultTsbCryptoService(CryptoProperties properties) {
        this.keyId = required(properties.getKeyId(), "tsb.shared.crypto.key-id");
        this.encryptionKey = decodeKey(properties.getEncryptionKey(), "tsb.shared.crypto.encryption-key");
        this.hashKey = decodeKey(properties.getHashKey(), "tsb.shared.crypto.hash-key");
        if (encryptionKey.length != 32) {
            throw new IllegalStateException("tsb.shared.crypto.encryption-key must be base64 for 32 bytes");
        }
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null || isEncrypted(plaintext)) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();
            return ENCRYPTED_PREFIX + keyId + ":" + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not encrypt field", ex);
        }
    }

    @Override
    public String decrypt(String value) {
        if (value == null || !isEncrypted(value)) {
            return value;
        }
        try {
            String[] parts = value.split(":", 4);
            if (parts.length != 4 || !keyId.equals(parts[2])) {
                throw new IllegalStateException("Unsupported crypto key id");
            }
            byte[] payload = Base64.getDecoder().decode(parts[3]);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not decrypt field", ex);
        }
    }

    @Override
    public String hashForLookup(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey, "HmacSHA256"));
            return HASH_PREFIX + keyId + ":" + Base64.getEncoder().encodeToString(mac.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash field", ex);
        }
    }

    @Override
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTED_PREFIX);
    }

    private static byte[] decodeKey(String value, String name) {
        return Base64.getDecoder().decode(required(value, name));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
