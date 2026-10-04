package vn.com.truongsonbank.common.entitlement.adapter.outbound.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface EntitlementGroupRepository extends JpaRepository<EntitlementGroupEntity, Long> {
    List<EntitlementGroupEntity> findAllByIdIn(Iterable<Long> ids);
}
