package vn.com.truongsonbank.common.config.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SpringDataConfigClientRepository extends JpaRepository<ConfigClientEntity, Long> {
    boolean existsByAppAndApiKeyHashAndEnabledTrue(String app, String apiKeyHash);

    Optional<ConfigClientEntity> findByApp(String app);
}
