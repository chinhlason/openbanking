package vn.com.truongsonbank.common.entitlement.adapter.outbound.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ServicePackageGroupRepository extends JpaRepository<ServicePackageGroupEntity, Long> {
    java.util.List<ServicePackageGroupEntity> findAllByPackageId(Long packageId);
    java.util.Optional<ServicePackageGroupEntity> findByPackageIdAndGroupId(Long packageId, Long groupId);
}
