package vn.com.truongsonbank.client;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface CryptoCustomerRepository extends JpaRepository<CryptoCustomerEntity, Long> {
    Optional<CryptoCustomerEntity> findByCccdHash(String cccdHash);
}
