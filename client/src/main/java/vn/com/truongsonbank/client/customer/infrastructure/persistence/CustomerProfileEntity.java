package vn.com.truongsonbank.client.customer.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import vn.com.truongsonbank.shared.crypto.EncryptedEntity;
import vn.com.truongsonbank.shared.crypto.EncryptedField;

import java.time.Instant;

@Entity
@EncryptedEntity
@Table(name = "customer_profile")
public class CustomerProfileEntity {
    @Id
    private String id;

    @EncryptedField(searchable = true, hashField = "phoneHash")
    @Column(length = 512)
    private String phone;
    @Column(length = 256, unique = true)
    private String phoneHash;

    @EncryptedField(searchable = true, hashField = "cccdHash")
    @Column(length = 512)
    private String cccd;
    @Column(length = 256, unique = true)
    private String cccdHash;

    private String fullName;
    private String dob;
    private String gender;
    @Column(length = 1024)
    private String address;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

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
