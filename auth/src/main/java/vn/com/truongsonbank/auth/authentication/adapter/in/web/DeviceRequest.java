package vn.com.truongsonbank.auth.authentication.adapter.in.web;

public record DeviceRequest(
        String deviceId,
        String deviceName,
        String platform,
        String osVersion,
        String appVersion,
        String publicKey
) {
}
