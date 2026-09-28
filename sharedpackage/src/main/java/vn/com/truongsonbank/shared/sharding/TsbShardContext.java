package vn.com.truongsonbank.shared.sharding;

import java.util.Optional;
import java.util.function.Supplier;

public final class TsbShardContext {
    private static final ThreadLocal<ShardRoute> CURRENT = new ThreadLocal<>();

    private TsbShardContext() {
    }

    public static Optional<ShardRoute> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void set(ShardRoute route) {
        CURRENT.set(route);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static <T> T runWith(ShardRoute route, Supplier<T> supplier) {
        ShardRoute previous = CURRENT.get();
        CURRENT.set(route);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
