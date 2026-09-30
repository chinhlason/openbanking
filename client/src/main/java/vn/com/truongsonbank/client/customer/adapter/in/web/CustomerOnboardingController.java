package vn.com.truongsonbank.client.customer.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.truongsonbank.client.customer.application.CustomerOnboardingService;
import vn.com.truongsonbank.shared.response.ResponseWrapper;

@RestController
@RequestMapping("/client/api/v1/onboarding")
@ResponseWrapper
class CustomerOnboardingController {
    private final CustomerOnboardingService service;

    CustomerOnboardingController(CustomerOnboardingService service) {
        this.service = service;
    }

    @PostMapping("/start")
    CustomerOnboardingService.OnboardingView start(@RequestBody StartRequest request) {
        return service.start(request.phone());
    }

    @PostMapping("/{id}/otp/verify")
    CustomerOnboardingService.OnboardingView verifyOtp(@PathVariable String id, @RequestBody OtpRequest request) {
        return service.verifyOtp(id, request.otp());
    }

    @PostMapping("/{id}/identity/qr")
    CustomerOnboardingService.OnboardingView verifyQr(@PathVariable String id,
                                                      @RequestBody CustomerOnboardingService.QrRequest request) {
        return service.verifyQr(id, request);
    }

    @PostMapping("/{id}/identity/nfc")
    CustomerOnboardingService.OnboardingView verifyNfc(@PathVariable String id,
                                                       @RequestBody CustomerOnboardingService.NfcRequest request) {
        return service.verifyNfc(id, request);
    }

    @PostMapping("/{id}/identity/liveness")
    CustomerOnboardingService.OnboardingView verifyLiveness(@PathVariable String id,
                                                            @RequestBody CustomerOnboardingService.LivenessRequest request) {
        return service.verifyLiveness(id, request);
    }

    @PostMapping("/{id}/pin")
    CustomerOnboardingService.CompleteResponse complete(@PathVariable String id,
                                                       @RequestHeader("DPoP") String dpop,
                                                       @RequestBody CustomerOnboardingService.CompleteRequest request,
                                                       HttpServletRequest httpRequest) {
        return service.complete(id, request, dpop, requestUrl(httpRequest));
    }

    @GetMapping("/{id}")
    CustomerOnboardingService.OnboardingView get(@PathVariable String id) {
        return service.get(id);
    }

    private String requestUrl(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURL().toString() : request.getRequestURL() + "?" + query;
    }

    record StartRequest(String phone) {
    }

    record OtpRequest(String otp) {
    }
}
