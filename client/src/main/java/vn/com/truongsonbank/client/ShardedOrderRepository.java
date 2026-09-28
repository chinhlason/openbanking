package vn.com.truongsonbank.client;

import org.springframework.data.jpa.repository.JpaRepository;

interface ShardedOrderRepository extends JpaRepository<ShardedOrderEntity, Long> {
}
