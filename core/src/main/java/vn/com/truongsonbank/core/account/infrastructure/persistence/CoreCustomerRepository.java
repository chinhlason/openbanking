package vn.com.truongsonbank.core.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CoreCustomerRepository extends JpaRepository<CoreCustomerEntity, String> {
}
