package vn.com.truongsonbank.client;

import java.util.Map;

public record CommonConfigSnapshot(
        String app,
        String profile,
        long version,
        Map<String, String> flat,
        Map<String, Object> nested
) {
}
