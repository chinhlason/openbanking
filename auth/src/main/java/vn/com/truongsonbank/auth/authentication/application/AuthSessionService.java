package vn.com.truongsonbank.auth.authentication.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import vn.com.truongsonbank.auth.authentication.adapter.in.web.DeviceRequest;
import vn.com.truongsonbank.auth.authentication.adapter.in.web.DeviceResponse;
import vn.com.truongsonbank.auth.authentication.adapter.in.web.SessionResponse;
import vn.com.truongsonbank.auth.authentication.adapter.in.web.SessionViewResponse;
import vn.com.truongsonbank.auth.authentication.config.AuthProperties;
import vn.com.truongsonbank.auth.authentication.domain.AuthSession;
import vn.com.truongsonbank.auth.authentication.infrastructure.persistence.AuthDeviceEntity;
import vn.com.truongsonbank.auth.authentication.infrastructure.persistence.AuthDeviceRepository;
import vn.com.truongsonbank.auth.authentication.infrastructure.persistence.AuthCustomerIdentityEntity;
import vn.com.truongsonbank.auth.authentication.infrastructure.persistence.AuthCustomerIdentityRepository;
import vn.com.truongsonbank.auth.authentication.infrastructure.persistence.AuthSessionEntity;
import vn.com.truongsonbank.auth.authentication.infrastructure.persistence.AuthSessionRepository;
import vn.com.truongsonbank.auth.authentication.infrastructure.security.DpopProofVerifier;
import vn.com.truongsonbank.shared.exception.UnauthorizedException;
import vn.com.truongsonbank.shared.response.TsbResponse;
import vn.com.truongsonbank.shared.tracing.TraceIdentityEnricher;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Service
public class AuthSessionService {
    private static final String PREFIX = "tsb:auth:session:";
    private final SecureRandom random = new SecureRandom();
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AuthProperties properties;
    private final AuthDeviceRepository deviceRepository;
    private final AuthSessionRepository sessionRepository;
    private final DpopProofVerifier dpopProofVerifier;
    private final AuthCustomerIdentityRepository identityRepository;
    private final RestClient common;
    private final TraceIdentityEnricher traceIdentityEnricher;

    AuthSessionService(StringRedisTemplate redis,
                       ObjectMapper objectMapper,
                       AuthProperties properties,
                       AuthDeviceRepository deviceRepository,
                       AuthSessionRepository sessionRepository,
                       DpopProofVerifier dpopProofVerifier,
                       AuthCustomerIdentityRepository identityRepository,
                       TraceIdentityEnricher traceIdentityEnricher) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.deviceRepository = deviceRepository;
        this.sessionRepository = sessionRepository;
        this.dpopProofVerifier = dpopProofVerifier;
        this.identityRepository = identityRepository;
        this.common = RestClient.builder().baseUrl(properties.getCommonBaseUrl()).build();
        this.traceIdentityEnricher = traceIdentityEnricher;
    }

    public SessionResponse create(Jwt jwt, DeviceRequest deviceRequest, String dpopJkt) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getSessionTtl());
        String sessionId = newSessionId();
        String username = username(jwt);
        AuthDeviceEntity device = upsertDevice(jwt.getSubject(), username, deviceRequest, dpopJkt, now);
        CustomerEntitlements entitlements = customerEntitlements(jwt.getSubject());
        AuthSession session = new AuthSession(
                sessionId,
                jwt.getSubject(),
                entitlements.customerId(),
                username,
                device.getDeviceId(),
                dpopJkt,
                device.isTrusted(),
                List.of(),
                entitlements.servicePackages(),
                now,
                expiresAt
        );
        saveSessionRow(session);
        saveRedisSession(session);
        traceIdentityEnricher.enrich(session.subject(), session.customerId());
        return toResponse(session);
    }

    public SessionResponse create(Jwt jwt) {
        return create(jwt, null, null);
    }

    public SessionResponse createTrusted(String subject, String username, DeviceRequest deviceRequest, String dpopJkt) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getSessionTtl());
        String sessionId = newSessionId();
        AuthDeviceEntity device = upsertDevice(subject, username, deviceRequest, dpopJkt, now);
        device.setTrusted(true);
        deviceRepository.save(device);
        CustomerEntitlements entitlements = customerEntitlements(subject);
        AuthSession session = new AuthSession(
                sessionId,
                subject,
                entitlements.customerId(),
                username,
                device.getDeviceId(),
                dpopJkt,
                true,
                List.of(),
                entitlements.servicePackages(),
                now,
                expiresAt
        );
        saveSessionRow(session);
        saveRedisSession(session);
        traceIdentityEnricher.enrich(session.subject(), session.customerId());
        return toResponse(session);
    }

    public java.util.List<DeviceResponse> devices(String sessionId) {
        AuthSession session = read(sessionId);
        return deviceRepository.findBySubjectOrderByLastSeenAtDesc(session.subject()).stream()
                .map(device -> new DeviceResponse(
                        device.getDeviceId(),
                        device.getDeviceName(),
                        device.getPlatform(),
                        device.getOsVersion(),
                        device.getAppVersion(),
                        device.isTrusted(),
                        device.getCreatedAt(),
                        device.getLastSeenAt()
                ))
                .toList();
    }

    public java.util.List<SessionViewResponse> sessions(String sessionId) {
        AuthSession session = read(sessionId);
        return sessionRepository.findBySubjectAndRevokedAtIsNullOrderByCreatedAtDesc(session.subject()).stream()
                .map(row -> new SessionViewResponse(
                        row.getSessionId(),
                        row.getUsername(),
                        row.getDeviceId(),
                        row.isTrustedDevice(),
                        row.getCreatedAt(),
                        row.getExpiresAt(),
                        row.getRevokedAt()
                ))
                .toList();
    }

    public void revoke(String currentSessionId, String targetSessionId) {
        AuthSession current = read(currentSessionId);
        AuthSessionEntity row = sessionRepository.findById(targetSessionId).orElseThrow(UnauthorizedException::new);
        if (!current.subject().equals(row.getSubject())) {
            throw new UnauthorizedException();
        }
        row.setRevokedAt(Instant.now());
        sessionRepository.save(row);
        redis.delete(PREFIX + targetSessionId);
    }

    public SessionResponse trustDevice(AuthSession session, String deviceId) {
        if (!session.deviceId().equals(deviceId)) {
            throw new UnauthorizedException();
        }
        AuthDeviceEntity device = deviceRepository.findById(deviceId).orElseThrow(UnauthorizedException::new);
        if (!session.subject().equals(device.getSubject())) {
            throw new UnauthorizedException();
        }
        device.setTrusted(true);
        deviceRepository.save(device);
        AuthSession trustedSession = new AuthSession(
                session.sessionId(),
                session.subject(),
                session.customerId(),
                session.username(),
                session.deviceId(),
                session.dpopJkt(),
                true,
                session.roles(),
                session.servicePackages(),
                session.createdAt(),
                session.expiresAt()
        );
        saveRedisSession(trustedSession);
        sessionRepository.findById(session.sessionId()).ifPresent(row -> {
            row.setTrustedDevice(true);
            sessionRepository.save(row);
        });
        return toResponse(trustedSession);
    }

    private void saveRedisSession(AuthSession session) {
        try {
            redis.opsForValue().set(PREFIX + session.sessionId(), objectMapper.writeValueAsString(session), properties.getSessionTtl());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public SessionResponse keepAlive(String sessionId) {
        AuthSession session = read(sessionId);
        AuthSession renewed = new AuthSession(
                session.sessionId(),
                session.subject(),
                session.customerId(),
                session.username(),
                session.deviceId(),
                session.dpopJkt(),
                session.trustedDevice(),
                session.roles(),
                session.servicePackages(),
                session.createdAt(),
                Instant.now().plus(properties.getSessionTtl())
        );
        saveRedisSession(renewed);
        sessionRepository.findById(sessionId).ifPresent(row -> {
            row.setExpiresAt(renewed.expiresAt());
            sessionRepository.save(row);
        });
        return toResponse(renewed);
    }

    private AuthDeviceEntity upsertDevice(String subject, String username, DeviceRequest request, String dpopJkt, Instant now) {
        String deviceId = request == null || isBlank(request.deviceId()) ? "unknown" : request.deviceId();
        if (request != null && !isBlank(request.publicKey())) {
            String deviceJkt = dpopProofVerifier.publicKeyThumbprint(request.publicKey());
            if (isBlank(dpopJkt) || !dpopJkt.equals(deviceJkt)) {
                throw new UnauthorizedException();
            }
        }
        AuthDeviceEntity device = deviceRepository.findById(deviceId).orElseGet(AuthDeviceEntity::new);
        boolean isNew = device.getDeviceId() == null;
        if (!isNew && !subject.equals(device.getSubject())) {
            throw new UnauthorizedException();
        }
        device.setDeviceId(deviceId);
        device.setSubject(subject);
        device.setUsername(username);
        if (request != null) {
            device.setDeviceName(request.deviceName());
            device.setPlatform(request.platform());
            device.setOsVersion(request.osVersion());
            device.setAppVersion(request.appVersion());
            if (!isBlank(request.publicKey()) && !Objects.equals(device.getPublicKey(), request.publicKey())) {
                device.setPublicKey(request.publicKey());
                device.setTrusted(false);
            }
        }
        device.setTrusted(!isNew && device.isTrusted());
        device.setCreatedAt(isNew ? now : device.getCreatedAt());
        device.setLastSeenAt(now);
        return deviceRepository.save(device);
    }


    private void saveSessionRow(AuthSession session) {
        AuthSessionEntity row = new AuthSessionEntity();
        row.setSessionId(session.sessionId());
        row.setSubject(session.subject());
        row.setUsername(session.username());
        row.setDeviceId(session.deviceId());
        row.setDpopJkt(session.dpopJkt());
        row.setTrustedDevice(session.trustedDevice());
        row.setCreatedAt(session.createdAt());
        row.setExpiresAt(session.expiresAt());
        sessionRepository.save(row);
    }

    public AuthSession read(String sessionId) {
        String json = redis.opsForValue().get(PREFIX + sessionId);
        if (json == null) {
            throw new UnauthorizedException();
        }
        try {
            return objectMapper.readValue(json, AuthSession.class);
        } catch (JsonProcessingException ex) {
            throw new UnauthorizedException();
        }
    }

    public SessionResponse bindCustomer(String sessionId, String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new UnauthorizedException();
        }
        AuthSession current = read(sessionId);
        AuthCustomerIdentityEntity identity = identityRepository.findById(current.subject())
                .orElseGet(AuthCustomerIdentityEntity::new);
        identity.setSubject(current.subject());
        identity.setCustomerId(customerId);
        identity.setUpdatedAt(Instant.now());
        identityRepository.save(identity);
        AuthSession updated = new AuthSession(current.sessionId(), current.subject(), customerId, current.username(),
                current.deviceId(), current.dpopJkt(), current.trustedDevice(), current.roles(),
                loadServicePackages(customerId), current.createdAt(), current.expiresAt());
        saveRedisSession(updated);
        return toResponse(updated);
    }

    private CustomerEntitlements customerEntitlements(String subject) {
        return identityRepository.findById(subject)
                .map(identity -> new CustomerEntitlements(identity.getCustomerId(), loadServicePackages(identity.getCustomerId())))
                .orElseGet(() -> new CustomerEntitlements(null, List.of()));
    }

    private List<String> loadServicePackages(String customerId) {
        TsbResponse<List<String>> response = common.get()
                .uri("/entitlements/admin/customers/{customerId}/service-packages", customerId)
                .header("X-Config-Admin-Key", properties.getCommonAdminKey())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return response == null || response.data() == null ? List.of() : response.data();
    }

    private SessionResponse toResponse(AuthSession session) {
        return new SessionResponse(
                session.sessionId(),
                properties.getSessionTtl().toSeconds(),
                session.expiresAt(),
                session.subject(),
                session.customerId(),
                session.username(),
                session.deviceId(),
                session.trustedDevice(),
                session.roles(),
                session.servicePackages()
        );
    }

    private String username(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return username == null ? jwt.getSubject() : username;
    }

    private String newSessionId() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CustomerEntitlements(String customerId, List<String> servicePackages) { }
}
