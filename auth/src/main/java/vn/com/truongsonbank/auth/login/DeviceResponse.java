package vn.com.truongsonbank.auth.login;

import java.time.Instant;

record DeviceResponse(
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
