package vn.com.truongsonbank.client.customer.application;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import vn.com.truongsonbank.client.CommonConfigClient;
import vn.com.truongsonbank.client.customer.config.CustomerOnboardingProperties;
import vn.com.truongsonbank.client.customer.domain.CustomerOnboardingErrors;
import vn.com.truongsonbank.client.customer.domain.CustomerOnboardingStatus;
import vn.com.truongsonbank.client.customer.infrastructure.persistence.CustomerAccountLinkEntity;
import vn.com.truongsonbank.client.customer.infrastructure.persistence.CustomerAccountLinkRepository;
import vn.com.truongsonbank.client.customer.infrastructure.persistence.CustomerOnboardingSessionEntity;
import vn.com.truongsonbank.client.customer.infrastructure.persistence.CustomerOnboardingSessionRepository;
import vn.com.truongsonbank.client.customer.infrastructure.persistence.CustomerProfileEntity;
import vn.com.truongsonbank.client.customer.infrastructure.persistence.CustomerProfileRepository;
import vn.com.truongsonbank.shared.crypto.TsbCryptoService;
import vn.com.truongsonbank.shared.exception.BusinessException;
import vn.com.truongsonbank.shared.exception.NotFoundException;
import vn.com.truongsonbank.shared.response.TsbResponse;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class CustomerOnboardingService {
    private static final Pattern PHONE = Pattern.compile("^(84|0)[0-9]{9,10}$");
    private static final Pattern PIN = Pattern.compile("^[0-9]{6}$");

    private final CustomerOnboardingSessionRepository sessions;
    private final CustomerProfileRepository profiles;
    private final CustomerAccountLinkRepository accountLinks;
    private final TsbCryptoService crypto;
    private final CustomerOnboardingProperties properties;
    private final CommonConfigClient config;
    private final RestClient common;
    private final RestClient auth;
    private final RestClient core;

    CustomerOnboardingService(CustomerOnboardingSessionRepository sessions,
                              CustomerProfileRepository profiles,
                              CustomerAccountLinkRepository accountLinks,
                              TsbCryptoService crypto,
                              CustomerOnboardingProperties properties,
                              CommonConfigClient config) {
        this.sessions = sessions;
        this.profiles = profiles;
        this.accountLinks = accountLinks;
        this.crypto = crypto;
        this.properties = properties;
        this.config = config;
        this.common = RestClient.builder().baseUrl(properties.getCommonBaseUrl()).build();
        this.auth = RestClient.builder().baseUrl(properties.getAuthBaseUrl()).build();
        this.core = RestClient.builder().baseUrl(properties.getCoreBaseUrl()).build();
    }

    @Transactional
    public OnboardingView start(String phone) {
        String normalizedPhone = normalizePhone(phone);
        String phoneHash = crypto.hashForLookup(normalizedPhone);
        profiles.findByPhoneHash(phoneHash).ifPresent(existing -> {
            throw new BusinessException(CustomerOnboardingErrors.DUPLICATE_CUSTOMER);
        });
        boolean otpEnabled = workflow().contains(Step.OTP);
        OtpSendResponse otp = otpEnabled ? unwrap(common.post()
                .uri("/3rd/sms/otp/send")
                .body(Map.of("phone", normalizedPhone))
                .retrieve()
                .body(new ParameterizedTypeReference<TsbResponse<OtpSendResponse>>() {
                })) : null;
        Instant now = Instant.now();
        CustomerOnboardingSessionEntity session = new CustomerOnboardingSessionEntity();
        session.setId("ob_" + UUID.randomUUID());
        session.setPhone(normalizedPhone);
        session.setOtpProviderSessionId(otp == null ? null : otp.providerSessionId());
        session.setStatus(CustomerOnboardingStatus.STARTED);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setExpiresAt(now.plus(properties.getSessionTtl()));
        session.setOtpExpiresAt(otpEnabled ? now.plus(properties.getOtpTtl()) : null);
        sessions.save(session);
        return view(session, otp == null ? null : Map.of("debugOtp", otp.debugOtp()));
    }

    @Transactional
    public OnboardingView verifyOtp(String id, String otp) {
        CustomerOnboardingSessionEntity session = requireReadyFor(id, Step.OTP);
        if (Instant.now().isAfter(session.getOtpExpiresAt())) {
            throw new BusinessException(CustomerOnboardingErrors.EXPIRED);
        }
        OtpVerifyResponse response = unwrap(common.post()
                .uri("/3rd/sms/otp/verify")
                .body(new OtpVerifyRequest(session.getOtpProviderSessionId(), session.getPhone(), otp))
                .retrieve()
                .body(new ParameterizedTypeReference<TsbResponse<OtpVerifyResponse>>() {
                }));
        if (!response.verified()) {
            throw new BusinessException(CustomerOnboardingErrors.INVALID_OTP);
        }
        session.setStatus(CustomerOnboardingStatus.OTP_VERIFIED);
        touch(session);
        return view(session, null);
    }

    @Transactional
    public OnboardingView verifyQr(String id, QrRequest request) {
        CustomerOnboardingSessionEntity session = requireReadyFor(id, Step.QR);
        session.setCccd(required(request.cccd()));
        session.setFullName(required(request.fullName()));
        session.setDob(request.dob());
        session.setGender(request.gender());
        session.setAddress(request.address());
        session.setStatus(CustomerOnboardingStatus.QR_VERIFIED);
        touch(session);
        return view(session, identity(session, true, false, false));
    }

    @Transactional
    public OnboardingView verifyNfc(String id, NfcRequest request) {
        CustomerOnboardingSessionEntity session = requireReadyFor(id, Step.NFC);
        if (session.getCccd() == null) {
            session.setCccd(required(request.documentNumber()));
        }
        NfcVerifyResponse response = unwrap(common.post()
                .uri("/3rd/nfc/cccd/verify")
                .body(new NfcVerifyRequest(request.providerSessionId(), session.getCccd()))
                .retrieve()
                .body(new ParameterizedTypeReference<TsbResponse<NfcVerifyResponse>>() {
                }));
        if (!response.verified() || !session.getCccd().equals(response.cccd())) {
            throw new BusinessException(CustomerOnboardingErrors.INVALID_STATE);
        }
        session.setFullName(response.fullName());
        session.setDob(response.dob());
        session.setGender(response.gender());
        session.setAddress(response.address());
        session.setStatus(CustomerOnboardingStatus.NFC_VERIFIED);
        touch(session);
        return view(session, identity(session, true, true, false));
    }

    @Transactional
    public OnboardingView verifyLiveness(String id, LivenessRequest request) {
        CustomerOnboardingSessionEntity session = requireReadyFor(id, Step.LIVENESS);
        LivenessVerifyResponse response = unwrap(common.post()
                .uri("/3rd/ekyc/liveness/verify")
                .body(new LivenessVerifyRequest(request.providerSessionId(), session.getCccd()))
                .retrieve()
                .body(new ParameterizedTypeReference<TsbResponse<LivenessVerifyResponse>>() {
                }));
        if (!response.live() || !response.faceMatched()) {
            throw new BusinessException(CustomerOnboardingErrors.INVALID_STATE);
        }
        session.setStatus(CustomerOnboardingStatus.LIVENESS_VERIFIED);
        touch(session);
        return view(session, Map.of("live", response.live(), "faceMatched", response.faceMatched(), "score", response.score()));
    }

    @Transactional
    public CompleteResponse complete(String id, CompleteRequest request, String dpopProof, String dpopHtu) {
        if (!PIN.matcher(nullToBlank(request.pin())).matches()) {
            throw new BusinessException(CustomerOnboardingErrors.PIN_WEAK);
        }
        CustomerOnboardingSessionEntity session = requireOneOf(id,
                readyStatusFor(Step.PIN),
                CustomerOnboardingStatus.AUTH_CREATED,
                CustomerOnboardingStatus.ACCOUNT_CREATED);

        AuthCompleteResponse authResponse;
        if (session.getSessionId() == null) {
            authResponse = unwrap(auth.post()
                    .uri("/v1/internal/onboarding/complete")
                    .header("X-Internal-Onboarding-Secret", properties.getAuthInternalSecret())
                    .header("DPoP", dpopProof)
                    .body(new AuthCompleteRequest(session.getPhone(), request.pin(), request.device(), "POST", dpopHtu))
                    .retrieve()
                    .body(new ParameterizedTypeReference<TsbResponse<AuthCompleteResponse>>() {
                    }));
            session.setAuthSubject(authResponse.subject());
            session.setSessionId(authResponse.sessionId());
            session.setStatus(CustomerOnboardingStatus.AUTH_CREATED);
            touch(session);
        } else {
            authResponse = new AuthCompleteResponse(session.getSessionId(), 600, null, session.getAuthSubject(), session.getPhone(),
                    request.device().deviceId(), true, java.util.List.of());
        }

        CustomerProfileEntity profile = saveProfile(session);
        OpenAccountResponse accountResponse;
        if (session.getAccountNumber() == null) {
            accountResponse = unwrap(core.post()
                    .uri("/core/api/v1/accounts/open")
                    .body(new OpenAccountRequest(profile.getId(), profile.getPhoneHash(), profile.getCccdHash(),
                            session.getCccd(), session.getFullName()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<TsbResponse<OpenAccountResponse>>() {
                    }));
            session.setAccountNumber(accountResponse.accountNumber());
            session.setStatus(CustomerOnboardingStatus.ACCOUNT_CREATED);
            touch(session);
        } else {
            accountResponse = new OpenAccountResponse(session.getAccountNumber());
        }
        saveAccountLink(profile.getId(), accountResponse.accountNumber());
        session.setCustomerId(profile.getId());
        session.setStatus(CustomerOnboardingStatus.COMPLETED);
        touch(session);
        return new CompleteResponse(session.getId(), session.getStatus(), profile.getId(), accountResponse.accountNumber(),
                authResponse.sessionId(), authResponse.expiresInSeconds(), authResponse.expiresAt(), authResponse.subject(),
                authResponse.username(), authResponse.deviceId(), authResponse.trustedDevice(), authResponse.roles(), Step.DONE.name());
    }

    @Transactional(readOnly = true)
    public OnboardingView get(String id) {
        return view(load(id), null);
    }

    private CustomerProfileEntity saveProfile(CustomerOnboardingSessionEntity session) {
        String phoneHash = crypto.hashForLookup(session.getPhone());
        String cccdHash = crypto.hashForLookup(session.getCccd());
        CustomerProfileEntity profile = profiles.findByPhoneHash(phoneHash)
                .or(() -> profiles.findByCccdHash(cccdHash))
                .orElseGet(CustomerProfileEntity::new);
        Instant now = Instant.now();
        if (profile.getId() == null) {
            profile.setId("cus_" + UUID.randomUUID());
            profile.setCreatedAt(now);
        }
        profile.setPhone(session.getPhone());
        profile.setCccd(session.getCccd());
        profile.setFullName(session.getFullName());
        profile.setDob(session.getDob());
        profile.setGender(session.getGender());
        profile.setAddress(session.getAddress());
        profile.setStatus("ACTIVE");
        profile.setUpdatedAt(now);
        return profiles.save(profile);
    }

    private void saveAccountLink(String customerId, String accountNumber) {
        CustomerAccountLinkEntity link = accountLinks.findByAccountNumber(accountNumber).orElseGet(CustomerAccountLinkEntity::new);
        Instant now = Instant.now();
        if (link.getId() == null) {
            link.setId("cal_" + UUID.randomUUID());
            link.setCreatedAt(now);
        }
        link.setCustomerId(customerId);
        link.setAccountNumber(accountNumber);
        link.setStatus("ACTIVE");
        link.setUpdatedAt(now);
        accountLinks.save(link);
    }

    private CustomerOnboardingSessionEntity require(String id, CustomerOnboardingStatus status) {
        CustomerOnboardingSessionEntity session = load(id);
        if (session.getStatus() != status) {
            throw new BusinessException(CustomerOnboardingErrors.INVALID_STATE);
        }
        return session;
    }

    private CustomerOnboardingSessionEntity requireReadyFor(String id, Step step) {
        return require(id, readyStatusFor(step));
    }

    private CustomerOnboardingStatus readyStatusFor(Step step) {
        CustomerOnboardingStatus status = CustomerOnboardingStatus.STARTED;
        for (Step item : workflow()) {
            if (item == step) {
                return status;
            }
            CustomerOnboardingStatus after = statusAfter(item);
            if (after != null) {
                status = after;
            }
        }
        throw new BusinessException(CustomerOnboardingErrors.INVALID_STATE);
    }

    private CustomerOnboardingSessionEntity requireOneOf(String id, CustomerOnboardingStatus... statuses) {
        CustomerOnboardingSessionEntity session = load(id);
        for (CustomerOnboardingStatus status : statuses) {
            if (session.getStatus() == status) {
                return session;
            }
        }
        throw new BusinessException(CustomerOnboardingErrors.INVALID_STATE);
    }

    private CustomerOnboardingSessionEntity load(String id) {
        CustomerOnboardingSessionEntity session = sessions.findById(id).orElseThrow(NotFoundException::new);
        if (Instant.now().isAfter(session.getExpiresAt())) {
            throw new BusinessException(CustomerOnboardingErrors.EXPIRED);
        }
        return session;
    }

    private void touch(CustomerOnboardingSessionEntity session) {
        session.setUpdatedAt(Instant.now());
        sessions.save(session);
    }

    private OnboardingView view(CustomerOnboardingSessionEntity session, Object extra) {
        return new OnboardingView(session.getId(), session.getStatus(), session.getExpiresAt(), session.getOtpExpiresAt(),
                mask(session.getPhone()), mask(session.getCccd()), session.getFullName(), session.getAccountNumber(),
                nextStep(session), extra);
    }

    private String nextStep(CustomerOnboardingSessionEntity session) {
        EnumSet<Step> done = completedSteps(session.getStatus());
        for (Step step : workflow()) {
            if (!done.contains(step)) {
                return step.name();
            }
        }
        return Step.DONE.name();
    }

    private List<Step> workflow() {
        String raw = config.getString("customer.onboarding.workflow", "PHONE,OTP,QR,NFC,LIVENESS,PIN");
        List<Step> steps = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toUpperCase)
                .map(Step::valueOf)
                .filter(step -> step != Step.DONE)
                .distinct()
                .toList();
        return steps.isEmpty() ? List.of(Step.PHONE, Step.OTP, Step.QR, Step.NFC, Step.LIVENESS, Step.PIN) : steps;
    }

    private EnumSet<Step> completedSteps(CustomerOnboardingStatus status) {
        EnumSet<Step> steps = EnumSet.of(Step.PHONE);
        if (status.ordinal() >= CustomerOnboardingStatus.OTP_VERIFIED.ordinal()) {
            steps.add(Step.OTP);
        }
        if (status.ordinal() >= CustomerOnboardingStatus.QR_VERIFIED.ordinal()) {
            steps.add(Step.QR);
        }
        if (status.ordinal() >= CustomerOnboardingStatus.NFC_VERIFIED.ordinal()) {
            steps.add(Step.NFC);
        }
        if (status.ordinal() >= CustomerOnboardingStatus.LIVENESS_VERIFIED.ordinal()) {
            steps.add(Step.LIVENESS);
        }
        if (status.ordinal() >= CustomerOnboardingStatus.COMPLETED.ordinal()) {
            steps.add(Step.PIN);
        }
        return steps;
    }

    private CustomerOnboardingStatus statusAfter(Step step) {
        return switch (step) {
            case PHONE -> CustomerOnboardingStatus.STARTED;
            case OTP -> CustomerOnboardingStatus.OTP_VERIFIED;
            case QR -> CustomerOnboardingStatus.QR_VERIFIED;
            case NFC -> CustomerOnboardingStatus.NFC_VERIFIED;
            case LIVENESS -> CustomerOnboardingStatus.LIVENESS_VERIFIED;
            case PIN, DONE -> CustomerOnboardingStatus.COMPLETED;
        };
    }

    private Map<String, Object> identity(CustomerOnboardingSessionEntity session, boolean qr, boolean nfc, boolean live) {
        return Map.of("cccdMasked", mask(session.getCccd()), "fullName", session.getFullName(),
                "qrVerified", qr, "nfcVerified", nfc, "livenessVerified", live);
    }

    private String normalizePhone(String phone) {
        String value = nullToBlank(phone).replace(" ", "");
        if (!PHONE.matcher(value).matches()) {
            throw new BusinessException(CustomerOnboardingErrors.INVALID_PHONE);
        }
        return value.startsWith("0") ? "84" + value.substring(1) : value;
    }

    private String required(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(CustomerOnboardingErrors.INVALID_STATE);
        }
        return value;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String mask(String value) {
        if (value == null || value.length() < 4) {
            return value;
        }
        return "*".repeat(Math.max(0, value.length() - 3)) + value.substring(value.length() - 3);
    }

    private <T> T unwrap(TsbResponse<T> response) {
        if (response == null || !response.success() || response.data() == null) {
            throw new BusinessException(CustomerOnboardingErrors.INVALID_STATE);
        }
        return response.data();
    }

    public record OnboardingView(String onboardingId, CustomerOnboardingStatus status, Instant expiresAt,
                                 Instant otpExpiresAt, String phoneMasked, String cccdMasked, String fullName,
                                 String accountNumber, String nextStep, Object extra) {
    }

    public record QrRequest(String cccd, String oldCccd, String fullName, String dob, String gender, String address,
                            String issueDate, String rawQr) {
    }

    public record NfcRequest(String providerSessionId, String documentNumber) {
    }

    public record LivenessRequest(String providerSessionId, String livenessToken) {
    }

    public record CompleteRequest(String pin, DeviceRequest device) {
    }

    public record DeviceRequest(String deviceId, String deviceName, String platform, String osVersion, String appVersion,
                                String publicKey) {
    }

    public record CompleteResponse(String onboardingId, CustomerOnboardingStatus status, String customerId,
                                   String accountNumber, String sessionId, long expiresInSeconds, Instant expiresAt,
                                   String subject, String username, String deviceId, boolean trustedDevice,
                                   java.util.List<String> roles, String nextStep) {
    }

    private enum Step {
        PHONE, OTP, QR, NFC, LIVENESS, PIN, DONE
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

    record AuthCompleteRequest(String username, String pin, DeviceRequest device, String dpopHtm, String dpopHtu) {
    }

    record AuthCompleteResponse(String sessionId, long expiresInSeconds, Instant expiresAt, String subject, String username,
                                String deviceId, boolean trustedDevice, java.util.List<String> roles) {
    }

    record OpenAccountRequest(String customerId, String phoneHash, String cccdHash, String cccd, String fullName) {
    }

    record OpenAccountResponse(String accountNumber) {
    }
}
