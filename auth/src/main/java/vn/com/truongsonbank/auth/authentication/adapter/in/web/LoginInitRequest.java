package vn.com.truongsonbank.auth.authentication.adapter.in.web;

public record LoginInitRequest(String loginType, String username, DeviceRequest device) {
}
