package vn.com.truongsonbank.shared.security;

import java.util.Optional;

public final class AuthContextHolder {
    private static final ThreadLocal<AuthContext> CONTEXT = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    public static Optional<AuthContext> current() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static void set(AuthContext context) {
        CONTEXT.set(context);
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
