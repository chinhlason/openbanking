package vn.com.truongsonbank.auth.authentication.adapter.in.web;

public record OnboardingCompleteRequest(
        String username,
        String pin,
        DeviceRequest device,
        String dpopHtm,
        String dpopHtu
) {
}
