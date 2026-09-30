package vn.com.truongsonbank.auth.login;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface AuthDeviceRepository extends JpaRepository<AuthDeviceEntity, String> {
    List<AuthDeviceEntity> findBySubjectOrderByLastSeenAtDesc(String subject);
}
