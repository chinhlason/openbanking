package vn.com.truongsonbank.client;

import org.springframework.data.jpa.repository.JpaRepository;

interface ReplicatedConfigRepository extends JpaRepository<ReplicatedConfigEntity, String> {
}
