package vn.com.truongsonbank.common.config.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataConfigAuditLogRepository extends JpaRepository<ConfigAuditLogEntity, Long> {
    List<ConfigAuditLogEntity> findTop100ByAppAndProfileOrderByCreatedAtDesc(String app, String profile);
}
