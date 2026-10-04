package vn.com.truongsonbank.common.entitlement.adapter.outbound.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntitlementSnapshotRepository extends JpaRepository<EntitlementSnapshotEntity, Long> {
    Optional<EntitlementSnapshotEntity> findBySubjectTypeAndSubjectId(String subjectType, String subjectId);
}
