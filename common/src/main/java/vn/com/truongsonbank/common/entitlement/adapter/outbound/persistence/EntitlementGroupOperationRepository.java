package vn.com.truongsonbank.common.entitlement.adapter.outbound.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EntitlementGroupOperationRepository extends JpaRepository<EntitlementGroupOperationEntity, Long> {
    java.util.List<EntitlementGroupOperationEntity> findAllByGroupIdIn(Iterable<Long> groupIds);
    java.util.Optional<EntitlementGroupOperationEntity> findByGroupIdAndOperationId(Long groupId, Long operationId);
}
