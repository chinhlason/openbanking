package vn.com.truongsonbank.shared.cache;

record CachedValue(Object value, boolean nullValue, long ttlNanos, long softExpireAtNanos) {
    static CachedValue of(Object value, long ttlNanos, long softExpireAtNanos) {
        return new CachedValue(value, value == null, ttlNanos, softExpireAtNanos);
    }

    Object unwrap() {
        return nullValue ? null : value;
    }

    boolean softExpired() {
        return System.nanoTime() >= softExpireAtNanos;
    }
}
