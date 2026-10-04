-- Common owns one database only. This script is executed against entitlementdb.
CREATE TABLE IF NOT EXISTS entitlement_operation (id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(100) NOT NULL UNIQUE, name VARCHAR(255) NOT NULL, domain VARCHAR(100) NOT NULL, status VARCHAR(20) NOT NULL, version BIGINT NOT NULL, created_at TIMESTAMP NOT NULL);
CREATE TABLE IF NOT EXISTS entitlement_group (id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(100) NOT NULL UNIQUE, name VARCHAR(255) NOT NULL, parent_id BIGINT NULL, status VARCHAR(20) NOT NULL, version BIGINT NOT NULL, created_at TIMESTAMP NOT NULL);
CREATE TABLE IF NOT EXISTS service_package (id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(100) NOT NULL UNIQUE, name VARCHAR(255) NOT NULL, status VARCHAR(20) NOT NULL, version BIGINT NOT NULL, created_at TIMESTAMP NOT NULL);
CREATE TABLE IF NOT EXISTS entitlement_group_operation (id BIGINT AUTO_INCREMENT PRIMARY KEY, group_id BIGINT NOT NULL, operation_id BIGINT NOT NULL, effect VARCHAR(10) NOT NULL, created_at TIMESTAMP NOT NULL, UNIQUE KEY uk_group_operation (group_id, operation_id));
CREATE TABLE IF NOT EXISTS service_package_group (id BIGINT AUTO_INCREMENT PRIMARY KEY, package_id BIGINT NOT NULL, group_id BIGINT NOT NULL, effect VARCHAR(10) NOT NULL, UNIQUE KEY uk_package_group (package_id, group_id));
CREATE TABLE IF NOT EXISTS customer_service_package (id BIGINT AUTO_INCREMENT PRIMARY KEY, customer_id VARCHAR(128) NOT NULL, package_id BIGINT NOT NULL, status VARCHAR(20) NOT NULL, effective_from TIMESTAMP NOT NULL, effective_to TIMESTAMP NULL);
CREATE TABLE IF NOT EXISTS user_entitlement_override (id BIGINT AUTO_INCREMENT PRIMARY KEY, subject_type VARCHAR(32) NOT NULL, subject_id VARCHAR(128) NOT NULL, operation_id BIGINT NOT NULL, effect VARCHAR(10) NOT NULL, reason VARCHAR(500) NOT NULL, approved_by VARCHAR(128) NOT NULL, effective_from TIMESTAMP NOT NULL, effective_to TIMESTAMP NULL);
CREATE TABLE IF NOT EXISTS entitlement_audit_log (id BIGINT AUTO_INCREMENT PRIMARY KEY, actor_id VARCHAR(128) NOT NULL, action VARCHAR(100) NOT NULL, target_type VARCHAR(100) NOT NULL, target_id VARCHAR(128) NOT NULL, trace_id VARCHAR(100), created_at TIMESTAMP NOT NULL);
CREATE TABLE IF NOT EXISTS entitlement_snapshot (id BIGINT AUTO_INCREMENT PRIMARY KEY, subject_type VARCHAR(32) NOT NULL, subject_id VARCHAR(128) NOT NULL, allow_operations TEXT NOT NULL, deny_operations TEXT NOT NULL, version BIGINT NOT NULL, expires_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, UNIQUE KEY idx_entitlement_snapshot_subject (subject_type, subject_id));

INSERT IGNORE INTO entitlement_group (code, name, parent_id, status, version, created_at)
VALUES ('STANDARD', 'Standard customer entitlements', NULL, 'ACTIVE', 1, CURRENT_TIMESTAMP);

INSERT IGNORE INTO service_package (code, name, status, version, created_at)
VALUES ('STANDARD', 'Standard service package', 'ACTIVE', 1, CURRENT_TIMESTAMP);

INSERT IGNORE INTO service_package_group (package_id, group_id, effect)
SELECT service_package.id, entitlement_group.id, 'ALLOW'
FROM service_package
JOIN entitlement_group ON entitlement_group.code = 'STANDARD'
WHERE service_package.code = 'STANDARD';
