package vn.com.truongsonbank.auth.login;

public record LoginRequest(String username, String pin, DeviceRequest device) {
}
