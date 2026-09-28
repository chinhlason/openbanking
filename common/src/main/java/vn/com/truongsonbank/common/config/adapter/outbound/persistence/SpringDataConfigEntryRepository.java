package vn.com.truongsonbank.common.config.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.com.truongsonbank.common.config.domain.model.ConfigStatus;

import java.util.List;

interface SpringDataConfigEntryRepository extends JpaRepository<ConfigEntryEntity, Long> {
    void deleteByAppAndProfileAndStatus(String app, String profile, ConfigStatus status);

    void deleteByAppAndProfileAndStatusAndKeyIn(String app, String profile, ConfigStatus status, List<String> keys);

    List<ConfigEntryEntity> findByAppAndProfileAndStatusOrderByKey(String app, String profile, ConfigStatus status);

    List<ConfigEntryEntity> findByAppAndProfileAndStatusAndKeyInOrderByKey(
            String app, String profile, ConfigStatus status, List<String> keys);

    List<ConfigEntryEntity> findByAppAndProfileAndStatusAndVersionOrderByKey(String app, String profile, ConfigStatus status, long version);

    @Query("""
            select coalesce(max(e.version), 0)
            from ConfigEntryEntity e
            where e.app = :app and e.profile = :profile and e.status = :status
            """)
    long maxVersion(String app, String profile, ConfigStatus status);
}
