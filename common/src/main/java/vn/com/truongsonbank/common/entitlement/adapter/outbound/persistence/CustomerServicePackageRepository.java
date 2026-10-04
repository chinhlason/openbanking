package vn.com.truongsonbank.common.entitlement.adapter.outbound.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CustomerServicePackageRepository extends JpaRepository<CustomerServicePackageEntity, Long> {
    boolean existsByCustomerIdAndPackageIdAndStatus(String customerId, Long packageId, String status);
    List<CustomerServicePackageEntity> findAllByCustomerIdAndStatus(String customerId, String status);
}
