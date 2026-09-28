package vn.com.truongsonbank.common.config.domain.model;

import java.util.Map;

public record ConfigSnapshot(
        String app,
        String profile,
        long version,
        Map<String, String> flat,
        Map<String, Object> nested
) {
}
