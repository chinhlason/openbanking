package vn.com.truongsonbank.auth.authentication.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "auth_customer_identity")
public class AuthCustomerIdentityEntity {
    @Id
    private String subject;
    @Column(nullable = false, unique = true)
    private String customerId;
    @Column(nullable = false)
    private Instant updatedAt;

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
