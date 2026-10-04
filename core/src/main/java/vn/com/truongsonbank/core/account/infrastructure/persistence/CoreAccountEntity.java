package vn.com.truongsonbank.core.account.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import vn.com.truongsonbank.core.account.domain.AccountStatus;

import java.time.Instant;

@Entity
@Table(name = "core_account", indexes = {
        @Index(name = "ix_core_account_customer_status", columnList = "customer_id,status")
})
public class CoreAccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "account_number", nullable = false, unique = true, length = 40)
    private String accountNumber;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "t29_account_reference", length = 80)
    private String t29AccountReference;

    @Column(nullable = false)
    private Instant openedAt;
    private Instant closedAt;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected CoreAccountEntity() {
    }

    public CoreAccountEntity(String customerId, String accountNumber, String currency, Instant now) {
        this.customerId = customerId;
        this.accountNumber = accountNumber;
        this.currency = currency;
        this.status = AccountStatus.ACTIVE;
        this.t29AccountReference = accountNumber;
        this.openedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getCurrency() {
        return currency;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public String getT29AccountReference() {
        return t29AccountReference;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }
}
