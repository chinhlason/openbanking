package vn.com.truongsonbank.common.entitlement.adapter.inbound.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import vn.com.truongsonbank.common.config.infrastructure.config.ConfigServerProperties;
import vn.com.truongsonbank.common.entitlement.adapter.outbound.persistence.EntitlementCatalogService;
import vn.com.truongsonbank.common.entitlement.domain.EntitlementErrors;
import vn.com.truongsonbank.shared.exception.TsbException;
import vn.com.truongsonbank.shared.response.ResponseWrapper;

@RestController
@RequestMapping("/entitlements/admin")
@ResponseWrapper
public class EntitlementAdminController {
    private final EntitlementCatalogService service;
    private final ConfigServerProperties properties;
    public EntitlementAdminController(EntitlementCatalogService service, ConfigServerProperties properties) { this.service = service; this.properties = properties; }

    @GetMapping("/metadata/packages/{code}")
    Map<String, Object> packageMetadata(@RequestHeader("X-Config-Admin-Key") String key, @PathVariable String code) {
        check(key); return service.packageMetadata(code);
    }

    @GetMapping("/operations")
    List<Map<String, Object>> operations(@RequestHeader("X-Config-Admin-Key") String key) {
        check(key); return service.operations();
    }

    @GetMapping("/groups")
    List<Map<String, Object>> groups(@RequestHeader("X-Config-Admin-Key") String key) {
        check(key); return service.groups();
    }

    @GetMapping("/service-packages")
    List<Map<String, Object>> servicePackages(@RequestHeader("X-Config-Admin-Key") String key) {
        check(key); return service.servicePackages();
    }

    @PostMapping("/operations")
    Map<String, Object> operation(@RequestHeader("X-Config-Admin-Key") String key, @RequestBody OperationRequest body, HttpServletRequest request) {
        check(key); return service.operation(body.code(), body.name(), body.domain(), actor(request), request.getHeader("traceparent"));
    }
    @PostMapping("/groups")
    Map<String, Object> group(@RequestHeader("X-Config-Admin-Key") String key, @RequestBody GroupRequest body, HttpServletRequest request) {
        check(key); return service.group(body.code(), body.name(), body.parentId(), actor(request), request.getHeader("traceparent"));
    }
    @PostMapping("/service-packages")
    Map<String, Object> servicePackage(@RequestHeader("X-Config-Admin-Key") String key, @RequestBody PackageRequest body, HttpServletRequest request) {
        check(key); return service.servicePackage(body.code(), body.name(), actor(request), request.getHeader("traceparent"));
    }
    @PostMapping("/groups/{groupId}/operations/{operationId}")
    Map<String, Object> groupOperation(@RequestHeader("X-Config-Admin-Key") String key, @PathVariable Long groupId, @PathVariable Long operationId, @RequestParam(defaultValue = "ALLOW") String effect, HttpServletRequest request) {
        check(key); service.groupOperation(groupId, operationId, effect, actor(request), request.getHeader("traceparent")); return Map.of("bound", true);
    }
    @PostMapping("/service-packages/{packageId}/groups/{groupId}")
    Map<String, Object> packageGroup(@RequestHeader("X-Config-Admin-Key") String key, @PathVariable Long packageId, @PathVariable Long groupId, @RequestParam(defaultValue = "ALLOW") String effect, HttpServletRequest request) {
        check(key); service.packageGroup(packageId, groupId, effect, actor(request), request.getHeader("traceparent")); return Map.of("bound", true);
    }
    @PostMapping("/customers/{customerId}/service-packages/{packageId}")
    Map<String, Object> assignPackage(@RequestHeader("X-Config-Admin-Key") String key, @PathVariable String customerId, @PathVariable Long packageId, @RequestBody AssignmentRequest body, HttpServletRequest request) {
        check(key); service.assignPackage(customerId, packageId, body.effectiveFrom(), body.effectiveTo(), actor(request), request.getHeader("traceparent")); return Map.of("assigned", true);
    }
    @PostMapping("/customers/{customerId}/service-packages/by-code/{code}")
    Map<String, Object> assignPackageByCode(@RequestHeader("X-Config-Admin-Key") String key, @PathVariable String customerId,
                                            @PathVariable String code, HttpServletRequest request) {
        check(key); service.assignPackage(customerId, code, actor(request), request.getHeader("traceparent")); return Map.of("assigned", true, "code", code);
    }
    @GetMapping("/customers/{customerId}/service-packages")
    List<String> customerServicePackages(@RequestHeader("X-Config-Admin-Key") String key, @PathVariable String customerId) {
        check(key); return service.customerServicePackages(customerId);
    }
    @PostMapping("/subjects/{type}/{id}/overrides")
    Map<String, Object> override(@RequestHeader("X-Config-Admin-Key") String key, @PathVariable String type, @PathVariable String id, @RequestBody OverrideRequest body, HttpServletRequest request) {
        check(key); service.override(type, id, body.operationId(), body.effect(), body.reason(), actor(request), body.effectiveFrom(), body.effectiveTo(), request.getHeader("traceparent")); return Map.of("created", true);
    }
    private void check(String key) { if (key == null || !key.equals(properties.getAdminApiKey())) throw new TsbException(EntitlementErrors.INVALID_ADMIN_KEY); }
    private String actor(HttpServletRequest request) { String value = request.getHeader("X-Actor-Id"); return value == null || value.isBlank() ? "local-admin" : value; }
    public record OperationRequest(String code, String name, String domain) { }
    public record GroupRequest(String code, String name, Long parentId) { }
    public record PackageRequest(String code, String name) { }
    public record AssignmentRequest(java.time.Instant effectiveFrom, java.time.Instant effectiveTo) { }
    public record OverrideRequest(Long operationId, String effect, String reason, java.time.Instant effectiveFrom, java.time.Instant effectiveTo) { }
}
