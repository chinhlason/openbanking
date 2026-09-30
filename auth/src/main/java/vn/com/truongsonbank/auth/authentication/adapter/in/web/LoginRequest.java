package vn.com.truongsonbank.auth.authentication.adapter.in.web;

public record LoginRequest(String username, String pin, DeviceRequest device) {
}
