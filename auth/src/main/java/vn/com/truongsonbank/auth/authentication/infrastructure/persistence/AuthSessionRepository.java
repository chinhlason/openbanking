package vn.com.truongsonbank.auth.authentication.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, String> {
    List<AuthSessionEntity> findBySubjectAndRevokedAtIsNullOrderByCreatedAtDesc(String subject);
}
