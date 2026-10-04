package vn.com.truongsonbank.core.account.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "core_account_operation", uniqueConstraints = @UniqueConstraint(
        name = "uk_core_account_operation_request", columnNames = {"operation_type", "request_id"}))
public class AccountOperationEntity {
    public enum Status { PENDING, SUCCESS, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "request_id", nullable = false, length = 160)
    private String requestId;
    @Column(name = "operation_type", nullable = false, length = 50)
    private String operationType;
    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;
    @Column(name = "account_id")
    private Long accountId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;
    @Column(name = "failure_code", length = 80)
    private String failureCode;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected AccountOperationEntity() {
    }

    public AccountOperationEntity(String requestId, String operationType, String customerId, Instant now) {
        this.requestId = requestId;
        this.operationType = operationType;
        this.customerId = customerId;
        this.status = Status.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getCustomerId() {
        return customerId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Status getStatus() {
        return status;
    }

    public void success(Long accountId) {
        this.accountId = accountId;
        this.status = Status.SUCCESS;
        this.failureCode = null;
        this.updatedAt = Instant.now();
    }

    public void failed(String failureCode) {
        this.status = Status.FAILED;
        this.failureCode = failureCode;
        this.updatedAt = Instant.now();
    }
}
