package vn.com.truongsonbank.auth.login;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, String> {
    List<AuthSessionEntity> findBySubjectAndRevokedAtIsNullOrderByCreatedAtDesc(String subject);
}
