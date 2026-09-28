package vn.com.truongsonbank.common.config.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataConfigPublishEventRepository extends JpaRepository<ConfigPublishEventEntity, Long> {
}
