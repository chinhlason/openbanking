package vn.com.truongsonbank.shared.security;

import java.time.Duration;

interface InternalAuthNonceStore {
    boolean markIfNew(String issuer, String nonce, Duration ttl);
}
