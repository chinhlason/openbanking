package vn.com.truongsonbank.common.entitlement.adapter.outbound.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface EntitlementOperationRepository extends JpaRepository<EntitlementOperationEntity, Long> {
    boolean existsByCode(String code);
    List<EntitlementOperationEntity> findAllByIdIn(Iterable<Long> ids);
}
