package vn.com.truongsonbank.common.entitlement.adapter.outbound.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EntitlementAuditRepository extends JpaRepository<EntitlementAuditEntity, Long> { }
