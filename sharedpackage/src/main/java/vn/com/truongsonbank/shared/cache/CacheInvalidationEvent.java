package vn.com.truongsonbank.shared.cache;

record CacheInvalidationEvent(boolean prefix, String key) {
    static CacheInvalidationEvent key(String key) {
        return new CacheInvalidationEvent(false, key);
    }

    static CacheInvalidationEvent prefix(String prefix) {
        return new CacheInvalidationEvent(true, prefix);
    }

    String encode() {
        return (prefix ? "P:" : "K:") + key;
    }

    static CacheInvalidationEvent decode(String value) {
        if (value == null || value.length() < 3) {
            return null;
        }
        if (value.startsWith("P:")) {
            return prefix(value.substring(2));
        }
        if (value.startsWith("K:")) {
            return key(value.substring(2));
        }
        return null;
    }
}
