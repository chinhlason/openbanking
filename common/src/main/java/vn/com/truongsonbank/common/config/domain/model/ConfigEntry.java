package vn.com.truongsonbank.common.config.domain.model;

import java.time.Instant;

public record ConfigEntry(
        Long id,
        String app,
        String profile,
        String key,
        String value,
        ConfigValueType valueType,
        long version,
        ConfigStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt
) {
}
