package vn.com.truongsonbank.common.thirdparty;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.truongsonbank.shared.response.ResponseWrapper;

@RestController
@ResponseWrapper
class ThirdPartyMockController {

    @PostMapping("/3rd/sms/otp/send")
    OtpSendResponse sendOtp(@RequestBody OtpSendRequest request) {
        return new OtpSendResponse("mock-otp-" + request.phone(), "123456", 300);
    }

    @PostMapping("/3rd/sms/otp/verify")
    OtpVerifyResponse verifyOtp(@RequestBody OtpVerifyRequest request) {
        return new OtpVerifyResponse("123456".equals(request.otp()));
    }

    @PostMapping("/3rd/nfc/cccd/verify")
    NfcVerifyResponse verifyNfc(@RequestBody NfcVerifyRequest request) {
        String cccd = blank(request.cccd()) ? "001201000123" : request.cccd();
        return new NfcVerifyResponse(true, cccd, "NGUYEN VAN A", "2001-01-01", "M", "Ha Noi");
    }

    @PostMapping("/3rd/ekyc/liveness/verify")
    LivenessVerifyResponse verifyLiveness(@RequestBody LivenessVerifyRequest request) {
        return new LivenessVerifyResponse(true, true, 0.98);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    record OtpSendRequest(String phone) {
    }

    record OtpSendResponse(String providerSessionId, String debugOtp, int expiresInSeconds) {
    }

    record OtpVerifyRequest(String providerSessionId, String phone, String otp) {
    }

    record OtpVerifyResponse(boolean verified) {
    }

    record NfcVerifyRequest(String providerSessionId, String cccd) {
    }

    record NfcVerifyResponse(boolean verified, String cccd, String fullName, String dob, String gender, String address) {
    }

    record LivenessVerifyRequest(String providerSessionId, String cccd) {
    }

    record LivenessVerifyResponse(boolean live, boolean faceMatched, double score) {
    }
}
