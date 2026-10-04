package vn.com.truongsonbank.common.entitlement.adapter.outbound.persistence;

import java.util.Map;
import java.util.List;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.truongsonbank.common.entitlement.domain.EntitlementErrors;
import vn.com.truongsonbank.shared.exception.TsbException;

@Service
public class EntitlementCatalogService {
    private final EntitlementOperationRepository operations;
    private final EntitlementGroupRepository groups;
    private final ServicePackageRepository packages;
    private final EntitlementGroupOperationRepository groupOperations;
    private final ServicePackageGroupRepository packageGroups;
    private final EntitlementAuditRepository audits;
    private final CustomerServicePackageRepository customerPackages;
    private final UserEntitlementOverrideRepository overrides;

    public EntitlementCatalogService(EntitlementOperationRepository operations, EntitlementGroupRepository groups,
                                     ServicePackageRepository packages, EntitlementGroupOperationRepository groupOperations,
                                     ServicePackageGroupRepository packageGroups, EntitlementAuditRepository audits,
                                     CustomerServicePackageRepository customerPackages, UserEntitlementOverrideRepository overrides) {
        this.operations = operations; this.groups = groups; this.packages = packages;
        this.groupOperations = groupOperations; this.packageGroups = packageGroups; this.audits = audits;
        this.customerPackages = customerPackages; this.overrides = overrides;
    }

    @Transactional
    public Map<String, Object> operation(String code, String name, String domain, String actor, String traceId) {
        if (operations.existsByCode(code)) {
            throw new TsbException(EntitlementErrors.DUPLICATE_CODE, code);
        }
        EntitlementOperationEntity saved = operations.save(new EntitlementOperationEntity(code, name, domain));
        audits.save(new EntitlementAuditEntity(actor, "CREATE", "OPERATION", String.valueOf(saved.id), traceId));
        return Map.of("id", saved.id, "code", saved.code, "version", saved.version);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> operations() {
        return operations.findAll().stream()
                .map(item -> Map.<String, Object>of("id", item.id, "code", item.code, "name", item.name, "domain", item.domain, "status", item.status))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> groups() {
        return groups.findAll().stream()
                .map(item -> Map.<String, Object>of("id", item.id, "code", item.code, "name", item.name,
                        "parentId", item.parentId == null ? "" : item.parentId, "status", item.status))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> servicePackages() {
        return packages.findAll().stream()
                .map(item -> Map.<String, Object>of("id", item.id, "code", item.code, "name", item.name, "status", item.status))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> packageMetadata(String code) {
        ServicePackageEntity servicePackage = packages.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Service package not found"));
        List<ServicePackageGroupEntity> bindings = packageGroups.findAllByPackageId(servicePackage.id);
        Set<Long> groupIds = bindings.stream().filter(binding -> "ALLOW".equalsIgnoreCase(binding.effect))
                .map(binding -> binding.groupId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<EntitlementGroupEntity> groupRows = groups.findAllByIdIn(groupIds);
        List<EntitlementGroupOperationEntity> operationBindings = groupOperations.findAllByGroupIdIn(groupIds);
        Set<Long> allowedOperationIds = operationBindings.stream()
                .filter(binding -> "ALLOW".equalsIgnoreCase(binding.effect))
                .map(binding -> binding.operationId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> deniedOperationIds = operationBindings.stream()
                .filter(binding -> "DENY".equalsIgnoreCase(binding.effect))
                .map(binding -> binding.operationId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> operationIds = new LinkedHashSet<>(allowedOperationIds);
        operationIds.addAll(deniedOperationIds);
        Map<Long, EntitlementOperationEntity> operationRows = operations.findAllByIdIn(operationIds).stream()
                .collect(Collectors.toMap(row -> row.id, row -> row));
        List<String> allow = allowedOperationIds.stream().map(operationRows::get).filter(java.util.Objects::nonNull)
                .map(row -> row.code).toList();
        List<String> deny = deniedOperationIds.stream().map(operationRows::get).filter(java.util.Objects::nonNull)
                .map(row -> row.code).toList();
        List<Map<String, Object>> groupMetadata = groupRows.stream().map(group -> {
            List<String> groupOperations = operationBindings.stream()
                    .filter(binding -> binding.groupId.equals(group.id) && "ALLOW".equalsIgnoreCase(binding.effect))
                    .map(binding -> operationRows.get(binding.operationId)).filter(java.util.Objects::nonNull)
                    .map(row -> row.code).toList();
            return Map.<String, Object>of("code", group.code, "operations", groupOperations);
        }).toList();
        long version = Math.max(1L, servicePackage.version);
        return Map.of("scopeType", "SERVICE_PACKAGE", "scopeId", servicePackage.code,
                "version", version, "groups", groupMetadata, "allow", allow, "deny", deny);
    }

    @Transactional
    public Map<String, Object> group(String code, String name, Long parentId, String actor, String traceId) {
        if (parentId != null && !groups.existsById(parentId)) throw new IllegalArgumentException("Parent group not found");
        EntitlementGroupEntity saved = groups.save(new EntitlementGroupEntity(code, name, parentId));
        audits.save(new EntitlementAuditEntity(actor, "CREATE", "GROUP", String.valueOf(saved.id), traceId));
        return Map.of("id", saved.id, "code", saved.code, "parentId", saved.parentId == null ? "" : saved.parentId);
    }

    @Transactional
    public Map<String, Object> servicePackage(String code, String name, String actor, String traceId) {
        ServicePackageEntity saved = packages.save(new ServicePackageEntity(code, name));
        audits.save(new EntitlementAuditEntity(actor, "CREATE", "SERVICE_PACKAGE", String.valueOf(saved.id), traceId));
        return Map.of("id", saved.id, "code", saved.code, "version", saved.version);
    }

    @Transactional
    public void groupOperation(Long groupId, Long operationId, String effect, String actor, String traceId) {
        if (!groups.existsById(groupId) || !operations.existsById(operationId)) throw new IllegalArgumentException("Group or operation not found");
        groupOperations.findByGroupIdAndOperationId(groupId, operationId).ifPresentOrElse(existing -> {
            existing.setEffect(effect);
            groupOperations.save(existing);
        }, () -> groupOperations.save(new EntitlementGroupOperationEntity(groupId, operationId, effect)));
        audits.save(new EntitlementAuditEntity(actor, "BIND", "GROUP_OPERATION", groupId + ":" + operationId, traceId));
    }

    @Transactional
    public void packageGroup(Long packageId, Long groupId, String effect, String actor, String traceId) {
        if (!packages.existsById(packageId) || !groups.existsById(groupId)) throw new IllegalArgumentException("Package or group not found");
        packageGroups.findByPackageIdAndGroupId(packageId, groupId).ifPresentOrElse(existing -> {
            existing.setEffect(effect);
            packageGroups.save(existing);
        }, () -> packageGroups.save(new ServicePackageGroupEntity(packageId, groupId, effect)));
        audits.save(new EntitlementAuditEntity(actor, "BIND", "PACKAGE_GROUP", packageId + ":" + groupId, traceId));
    }

    @Transactional
    public void assignPackage(String customerId, Long packageId, Instant from, Instant to, String actor, String traceId) {
        if (customerId == null || customerId.isBlank() || packageId == null) {
            throw new TsbException(EntitlementErrors.REQUEST_INVALID);
        }
        if (!packages.existsById(packageId)) {
            throw new TsbException(EntitlementErrors.NOT_FOUND, "servicePackage", packageId);
        }
        if (customerPackages.existsByCustomerIdAndPackageIdAndStatus(customerId, packageId, "ACTIVE")) {
            return;
        }
        Instant effectiveFrom = effectiveFrom(from, to);
        customerPackages.save(new CustomerServicePackageEntity(customerId, packageId, effectiveFrom, to));
        audits.save(new EntitlementAuditEntity(actor, "ASSIGN", "CUSTOMER_PACKAGE", customerId + ":" + packageId, traceId));
    }

    @Transactional
    public void assignPackage(String customerId, String packageCode, String actor, String traceId) {
        ServicePackageEntity servicePackage = packages.findByCode(packageCode)
                .orElseThrow(() -> new TsbException(EntitlementErrors.NOT_FOUND, "servicePackage", packageCode));
        assignPackage(customerId, servicePackage.id, null, null, actor, traceId);
    }

    @Transactional(readOnly = true)
    public List<String> customerServicePackages(String customerId) {
        Instant now = Instant.now();
        Set<Long> packageIds = customerPackages.findAllByCustomerIdAndStatus(customerId, "ACTIVE").stream()
                .filter(binding -> !binding.effectiveFrom.isAfter(now))
                .filter(binding -> binding.effectiveTo == null || binding.effectiveTo.isAfter(now))
                .map(binding -> binding.packageId)
                .collect(Collectors.toSet());
        return packages.findAllById(packageIds).stream()
                .filter(servicePackage -> "ACTIVE".equals(servicePackage.status))
                .map(servicePackage -> servicePackage.code)
                .sorted()
                .toList();
    }

    @Transactional
    public void override(String type, String subjectId, Long operationId, String effect, String reason, String actor,
                         Instant from, Instant to, String traceId) {
        if (type == null || type.isBlank() || subjectId == null || subjectId.isBlank()
                || operationId == null || effect == null || effect.isBlank() || reason == null || reason.isBlank()) {
            throw new TsbException(EntitlementErrors.REQUEST_INVALID);
        }
        if (!operations.existsById(operationId)) {
            throw new TsbException(EntitlementErrors.NOT_FOUND, "operation", operationId);
        }
        Instant effectiveFrom = effectiveFrom(from, to);
        overrides.save(new UserEntitlementOverrideEntity(type, subjectId, operationId, effect, reason, actor, effectiveFrom, to));
        audits.save(new EntitlementAuditEntity(actor, "OVERRIDE", type, subjectId + ":" + operationId, traceId));
    }

    private Instant effectiveFrom(Instant from, Instant to) {
        Instant effectiveFrom = from == null ? Instant.now() : from;
        if (to != null && !to.isAfter(effectiveFrom)) {
            throw new TsbException(EntitlementErrors.REQUEST_INVALID);
        }
        return effectiveFrom;
    }
}
