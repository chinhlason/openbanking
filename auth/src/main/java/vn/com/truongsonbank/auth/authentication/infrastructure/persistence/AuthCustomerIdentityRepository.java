package vn.com.truongsonbank.auth.authentication.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthCustomerIdentityRepository extends JpaRepository<AuthCustomerIdentityEntity, String> {
}
