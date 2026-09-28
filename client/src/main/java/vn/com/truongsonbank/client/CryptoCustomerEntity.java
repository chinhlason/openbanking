package vn.com.truongsonbank.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import vn.com.truongsonbank.shared.crypto.EncryptedEntity;
import vn.com.truongsonbank.shared.crypto.EncryptedField;

@Entity
@EncryptedEntity
@Table(name = "crypto_customer_demo")
class CryptoCustomerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @EncryptedField(searchable = true, hashField = "cccdHash")
    @Column(length = 512)
    private String cccd;

    @Column(name = "cccd_hash", length = 256)
    private String cccdHash;

    @EncryptedField
    @Column(name = "full_name", length = 512)
    private String fullName;

    Long getId() {
        return id;
    }

    String getCccd() {
        return cccd;
    }

    void setCccd(String cccd) {
        this.cccd = cccd;
    }

    String getCccdHash() {
        return cccdHash;
    }

    String getFullName() {
        return fullName;
    }

    void setFullName(String fullName) {
        this.fullName = fullName;
    }
}
