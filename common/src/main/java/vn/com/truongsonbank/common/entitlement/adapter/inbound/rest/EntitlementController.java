package vn.com.truongsonbank.common.entitlement.adapter.inbound.rest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.truongsonbank.common.config.infrastructure.config.ConfigServerProperties;
import vn.com.truongsonbank.common.entitlement.application.EntitlementService;
import vn.com.truongsonbank.common.entitlement.domain.EntitlementErrors;
import vn.com.truongsonbank.shared.exception.TsbException;
import vn.com.truongsonbank.shared.response.ResponseWrapper;

@RestController
@RequestMapping("/entitlements")
@ResponseWrapper
public class EntitlementController {
    private final EntitlementService service;
    private final ConfigServerProperties properties;

    public EntitlementController(EntitlementService service, ConfigServerProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping("/subjects/{type}/{id}")
    Map<String, Object> get(@PathVariable String type, @PathVariable String id) {
        return service.get(type, id);
    }

    @GetMapping("/internal/subjects/{type}/{id}/operations")
    Map<String, Object> resolveInternal(@PathVariable String type, @PathVariable String id) {
        if (!"SERVICE".equalsIgnoreCase(type)) {
            throw new TsbException(EntitlementErrors.REQUEST_INVALID);
        }
        return service.resolveService(id);
    }

    @PostMapping("/subjects/{type}/{id}")
    Map<String, Object> upsert(@PathVariable String type, @PathVariable String id,
                               @RequestHeader("X-Config-Admin-Key") String adminKey,
                               @RequestBody SnapshotRequest request) {
        if (adminKey == null || !adminKey.equals(properties.getAdminApiKey())) {
            throw new TsbException(EntitlementErrors.INVALID_ADMIN_KEY);
        }
        return service.upsert(type, id, request.allow(), request.deny(), request.version(), request.expiresAt());
    }

    public record SnapshotRequest(List<String> allow, List<String> deny, Long version, Instant expiresAt) {
    }
}
