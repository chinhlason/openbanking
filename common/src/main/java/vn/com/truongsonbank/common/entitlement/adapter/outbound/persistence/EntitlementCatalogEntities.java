package vn.com.truongsonbank.common.entitlement.adapter.outbound.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "entitlement_operation", uniqueConstraints = @UniqueConstraint(name = "uk_entitlement_operation_code", columnNames = "code"))
class EntitlementOperationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 100) String code;
    @Column(nullable = false, length = 255) String name;
    @Column(nullable = false, length = 100) String domain;
    @Column(nullable = false, length = 20) String status = "ACTIVE";
    @Column(nullable = false) long version = 1;
    @Column(nullable = false) Instant createdAt = Instant.now();
    protected EntitlementOperationEntity() {}
    EntitlementOperationEntity(String code, String name, String domain) { this.code = code; this.name = name; this.domain = domain; }
}

@Entity
@Table(name = "entitlement_group", uniqueConstraints = @UniqueConstraint(name = "uk_entitlement_group_code", columnNames = "code"))
class EntitlementGroupEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 100) String code;
    @Column(nullable = false, length = 255) String name;
    Long parentId;
    @Column(nullable = false, length = 20) String status = "ACTIVE";
    @Column(nullable = false) long version = 1;
    @Column(nullable = false) Instant createdAt = Instant.now();
    protected EntitlementGroupEntity() {}
    EntitlementGroupEntity(String code, String name, Long parentId) { this.code = code; this.name = name; this.parentId = parentId; }
}

@Entity
@Table(name = "service_package", uniqueConstraints = @UniqueConstraint(name = "uk_service_package_code", columnNames = "code"))
class ServicePackageEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 100) String code;
    @Column(nullable = false, length = 255) String name;
    @Column(nullable = false, length = 20) String status = "ACTIVE";
    @Column(nullable = false) long version = 1;
    @Column(nullable = false) Instant createdAt = Instant.now();
    protected ServicePackageEntity() {}
    ServicePackageEntity(String code, String name) { this.code = code; this.name = name; }
}

@Entity
@Table(name = "entitlement_group_operation", uniqueConstraints = @UniqueConstraint(name = "uk_group_operation", columnNames = {"groupId", "operationId"}))
class EntitlementGroupOperationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false) Long groupId;
    @Column(nullable = false) Long operationId;
    @Column(nullable = false, length = 10) String effect;
    @Column(nullable = false) Instant createdAt = Instant.now();
    protected EntitlementGroupOperationEntity() {}
    EntitlementGroupOperationEntity(Long groupId, Long operationId, String effect) { this.groupId = groupId; this.operationId = operationId; this.effect = effect; }
    void setEffect(String effect) { this.effect = effect; }
}

@Entity
@Table(name = "service_package_group", uniqueConstraints = @UniqueConstraint(name = "uk_package_group", columnNames = {"packageId", "groupId"}))
class ServicePackageGroupEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false) Long packageId;
    @Column(nullable = false) Long groupId;
    @Column(nullable = false, length = 10) String effect;
    protected ServicePackageGroupEntity() {}
    ServicePackageGroupEntity(Long packageId, Long groupId, String effect) { this.packageId = packageId; this.groupId = groupId; this.effect = effect; }
    void setEffect(String effect) { this.effect = effect; }
}

@Entity
@Table(name = "customer_service_package")
class CustomerServicePackageEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 128) String customerId;
    @Column(nullable = false) Long packageId;
    @Column(nullable = false, length = 20) String status = "ACTIVE";
    @Column(nullable = false) Instant effectiveFrom;
    Instant effectiveTo;
    protected CustomerServicePackageEntity() {}
    CustomerServicePackageEntity(String customerId, Long packageId, Instant from, Instant to) { this.customerId = customerId; this.packageId = packageId; this.effectiveFrom = from; this.effectiveTo = to; }
}

@Entity
@Table(name = "user_entitlement_override")
class UserEntitlementOverrideEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 32) String subjectType;
    @Column(nullable = false, length = 128) String subjectId;
    @Column(nullable = false) Long operationId;
    @Column(nullable = false, length = 10) String effect;
    @Column(nullable = false, length = 500) String reason;
    @Column(nullable = false, length = 128) String approvedBy;
    @Column(nullable = false) Instant effectiveFrom;
    Instant effectiveTo;
    protected UserEntitlementOverrideEntity() {}
    UserEntitlementOverrideEntity(String type, String subject, Long operation, String effect, String reason, String actor, Instant from, Instant to) { this.subjectType = type; this.subjectId = subject; this.operationId = operation; this.effect = effect; this.reason = reason; this.approvedBy = actor; this.effectiveFrom = from; this.effectiveTo = to; }
}

@Entity
@Table(name = "entitlement_audit_log")
class EntitlementAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 128) String actorId;
    @Column(nullable = false, length = 100) String action;
    @Column(nullable = false, length = 100) String targetType;
    @Column(nullable = false, length = 128) String targetId;
    @Column(length = 100) String traceId;
    @Column(nullable = false) Instant createdAt = Instant.now();
    protected EntitlementAuditEntity() {}
    EntitlementAuditEntity(String actor, String action, String type, String target, String trace) { this.actorId = actor; this.action = action; this.targetType = type; this.targetId = target; this.traceId = trace; }
}
