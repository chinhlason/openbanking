package vn.com.truongsonbank.shared.crypto;

public interface TsbCryptoService {
    String encrypt(String plaintext);

    String decrypt(String value);

    String hashForLookup(String plaintext);

    boolean isEncrypted(String value);
}
