package vn.com.truongsonbank.auth.authentication.adapter.in.web;

import java.time.Instant;

public record DeviceResponse(
        String deviceId,
        String deviceName,
        String platform,
        String osVersion,
        String appVersion,
        boolean trusted,
        Instant createdAt,
        Instant lastSeenAt
) {
}
