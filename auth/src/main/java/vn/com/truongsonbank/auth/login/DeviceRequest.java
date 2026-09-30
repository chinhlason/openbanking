package vn.com.truongsonbank.auth.login;

public record DeviceRequest(
        String deviceId,
        String deviceName,
        String platform,
        String osVersion,
        String appVersion,
        String publicKey
) {
}
