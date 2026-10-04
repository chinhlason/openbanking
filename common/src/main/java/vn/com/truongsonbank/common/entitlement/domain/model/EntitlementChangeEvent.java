package vn.com.truongsonbank.common.entitlement.domain.model;

import java.time.Instant;
import java.util.UUID;

public record EntitlementChangeEvent(
        String eventId,
        String changeType,
        String scope,
        String packageCode,
        String subjectId,
        Instant occurredAt) {

    public static EntitlementChangeEvent all(String changeType) {
        return new EntitlementChangeEvent(UUID.randomUUID().toString(), changeType, "ALL", null, null, Instant.now());
    }

    public static EntitlementChangeEvent packageMetadata(String changeType, String packageCode) {
        return new EntitlementChangeEvent(UUID.randomUUID().toString(), changeType, "PACKAGE", packageCode, null, Instant.now());
    }

    public static EntitlementChangeEvent subject(String changeType, String subjectId) {
        return new EntitlementChangeEvent(UUID.randomUUID().toString(), changeType, "SUBJECT", null, subjectId, Instant.now());
    }
}
