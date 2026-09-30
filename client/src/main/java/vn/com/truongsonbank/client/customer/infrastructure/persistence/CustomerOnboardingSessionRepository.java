package vn.com.truongsonbank.client.customer.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOnboardingSessionRepository extends JpaRepository<CustomerOnboardingSessionEntity, String> {
}
