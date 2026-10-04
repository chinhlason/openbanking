package vn.com.truongsonbank.shared.security;

import java.util.Optional;

public final class ServiceSecurityContextHolder {
    private static final ThreadLocal<ServicePrincipal> CURRENT = new ThreadLocal<>();

    private ServiceSecurityContextHolder() {
    }

    public static Optional<ServicePrincipal> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    static void set(ServicePrincipal principal) {
        CURRENT.set(principal);
    }

    static void clear() {
        CURRENT.remove();
    }
}
