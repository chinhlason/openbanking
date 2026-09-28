package vn.com.truongsonbank.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import vn.com.truongsonbank.shared.sharding.ShardEntity;
import vn.com.truongsonbank.shared.sharding.ShardKey;
import vn.com.truongsonbank.shared.sharding.ShardStrategy;
import vn.com.truongsonbank.shared.sharding.ShardTimeKey;
import vn.com.truongsonbank.shared.sharding.ShardTimeUnit;

import java.time.LocalDateTime;

@Entity
@Table(name = "t_order")
@ShardEntity(
        db = 2,
        tb = 12,
        dbStrategy = ShardStrategy.HASH,
        tbStrategy = ShardStrategy.TIME,
        timeUnit = ShardTimeUnit.MONTH,
        tablePattern = "t_order_${yyyyMM}"
)
class ShardedOrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ShardKey
    @Column(name = "user_id")
    private Long userId;

    @ShardTimeKey
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "amount")
    private Long amount;

    Long getId() {
        return id;
    }

    Long getUserId() {
        return userId;
    }

    void setUserId(Long userId) {
        this.userId = userId;
    }

    LocalDateTime getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    Long getAmount() {
        return amount;
    }

    void setAmount(Long amount) {
        this.amount = amount;
    }
}
