package vn.com.truongsonbank.shared.security;

import java.time.Instant;
import java.util.List;

public record ServicePrincipal(
        String serviceCode,
        String subject,
        List<String> audiences,
        String issuer,
        String tokenId,
        Instant expiresAt) {
    public ServicePrincipal {
        audiences = audiences == null ? List.of() : List.copyOf(audiences);
    }
}
