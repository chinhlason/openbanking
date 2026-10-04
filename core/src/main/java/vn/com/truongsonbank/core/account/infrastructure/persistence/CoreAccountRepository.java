package vn.com.truongsonbank.core.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.truongsonbank.core.account.domain.AccountStatus;

import java.util.List;
import java.util.Optional;

public interface CoreAccountRepository extends JpaRepository<CoreAccountEntity, Long> {
    List<CoreAccountEntity> findAllByCustomerIdAndStatusOrderByOpenedAtAsc(String customerId, AccountStatus status);

    Optional<CoreAccountEntity> findByAccountNumber(String accountNumber);
}
