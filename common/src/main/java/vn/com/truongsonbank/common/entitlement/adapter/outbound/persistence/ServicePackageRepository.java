package vn.com.truongsonbank.common.entitlement.adapter.outbound.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ServicePackageRepository extends JpaRepository<ServicePackageEntity, Long> {
    java.util.Optional<ServicePackageEntity> findByCode(String code);
}
