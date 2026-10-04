package vn.com.truongsonbank.core.account.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "core_customer")
public class CoreCustomerEntity {
    @Id
    private String customerId;
    private String phoneHash;
    private String cccdHash;
    private String fullName;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    protected CoreCustomerEntity() {
    }

    public CoreCustomerEntity(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getPhoneHash() {
        return phoneHash;
    }

    public void setPhoneHash(String phoneHash) {
        this.phoneHash = phoneHash;
    }

    public String getCccdHash() {
        return cccdHash;
    }

    public void setCccdHash(String cccdHash) {
        this.cccdHash = cccdHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
