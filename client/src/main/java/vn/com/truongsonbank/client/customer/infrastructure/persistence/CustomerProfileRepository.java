package vn.com.truongsonbank.client.customer.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfileEntity, String> {
    Optional<CustomerProfileEntity> findByPhoneHash(String phoneHash);

    Optional<CustomerProfileEntity> findByCccdHash(String cccdHash);
}
