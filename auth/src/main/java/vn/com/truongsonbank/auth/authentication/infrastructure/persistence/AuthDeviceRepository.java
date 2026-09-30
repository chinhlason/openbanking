package vn.com.truongsonbank.auth.authentication.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuthDeviceRepository extends JpaRepository<AuthDeviceEntity, String> {
    List<AuthDeviceEntity> findBySubjectOrderByLastSeenAtDesc(String subject);
}
