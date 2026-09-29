package vn.com.truongsonbank.shared.config;

import java.util.Map;

public record TsbCommonConfigSnapshot(
        String app,
        String profile,
        long version,
        Map<String, String> flat,
        Map<String, Object> nested
) {
}
