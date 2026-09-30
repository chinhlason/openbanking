package vn.com.truongsonbank.client.customer.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import vn.com.truongsonbank.client.customer.domain.CustomerOnboardingStatus;
import vn.com.truongsonbank.shared.crypto.EncryptedEntity;
import vn.com.truongsonbank.shared.crypto.EncryptedField;

import java.time.Instant;

@Entity
@EncryptedEntity
@Table(name = "customer_onboarding_session")
public class CustomerOnboardingSessionEntity {
    @Id
    private String id;
    @EncryptedField(searchable = true, hashField = "phoneHash")
    @Column(length = 512)
    private String phone;
    @Column(length = 256)
    private String phoneHash;
    @EncryptedField(searchable = true, hashField = "cccdHash")
    @Column(length = 512)
    private String cccd;
    @Column(length = 256)
    private String cccdHash;
    private String fullName;
    private String dob;
    private String gender;
    @Column(length = 1024)
    private String address;
    private String otpProviderSessionId;
    @Enumerated(EnumType.STRING)
    private CustomerOnboardingStatus status;
    private String customerId;
    private String authSubject;
    @Column(length = 1024)
    private String sessionId;
    private String accountNumber;
    private Instant expiresAt;
    private Instant otpExpiresAt;
    private Instant createdAt;
    private Instant updatedAt;
    private String failureCode;
    @Column(length = 1024)
    private String failureMessage;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPhoneHash() {
        return phoneHash;
    }

    public String getCccd() {
        return cccd;
    }

    public void setCccd(String cccd) {
        this.cccd = cccd;
    }

    public String getCccdHash() {
        return cccdHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDob() {
        return dob;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getOtpProviderSessionId() {
        return otpProviderSessionId;
    }

    public void setOtpProviderSessionId(String otpProviderSessionId) {
        this.otpProviderSessionId = otpProviderSessionId;
    }

    public CustomerOnboardingStatus getStatus() {
        return status;
    }

    public void setStatus(CustomerOnboardingStatus status) {
        this.status = status;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getAuthSubject() {
        return authSubject;
    }

    public void setAuthSubject(String authSubject) {
        this.authSubject = authSubject;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getOtpExpiresAt() {
        return otpExpiresAt;
    }

    public void setOtpExpiresAt(Instant otpExpiresAt) {
        this.otpExpiresAt = otpExpiresAt;
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

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }
}
