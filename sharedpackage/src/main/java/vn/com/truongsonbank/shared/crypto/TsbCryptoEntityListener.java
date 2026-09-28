package vn.com.truongsonbank.shared.crypto;

import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

public class TsbCryptoEntityListener {
    private static volatile TsbCryptoService cryptoService;

    static void setCryptoService(TsbCryptoService service) {
        cryptoService = service;
    }

    @PrePersist
    @PreUpdate
    public void encrypt(Object entity) {
        TsbCryptoEntityProcessor.encrypt(entity, service());
    }

    @PostLoad
    public void decrypt(Object entity) {
        TsbCryptoEntityProcessor.decrypt(entity, service());
    }

    private TsbCryptoService service() {
        TsbCryptoService service = cryptoService;
        if (service == null) {
            throw new IllegalStateException("TsbCryptoService is not configured");
        }
        return service;
    }
}
