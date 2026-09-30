package vn.com.truongsonbank.client.customer.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerAccountLinkRepository extends JpaRepository<CustomerAccountLinkEntity, String> {
    Optional<CustomerAccountLinkEntity> findByAccountNumber(String accountNumber);
}
