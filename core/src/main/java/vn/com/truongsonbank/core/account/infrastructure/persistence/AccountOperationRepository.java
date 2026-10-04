package vn.com.truongsonbank.core.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountOperationRepository extends JpaRepository<AccountOperationEntity, Long> {
    Optional<AccountOperationEntity> findByOperationTypeAndRequestId(String operationType, String requestId);
}
