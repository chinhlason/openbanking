package vn.com.truongsonbank.common.config.domain.model;

import java.time.Instant;
import java.util.List;

public record ConfigPublishEvent(
        String app,
        String profile,
        long version,
        List<String> changedKeys,
        Instant publishedAt
) {
}
