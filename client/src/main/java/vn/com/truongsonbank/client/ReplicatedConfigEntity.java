package vn.com.truongsonbank.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import vn.com.truongsonbank.shared.sharding.ReplicatedEntity;

@Entity
@Table(name = "bank_config")
@ReplicatedEntity
class ReplicatedConfigEntity {
    @Id
    @Column(name = "code", length = 64)
    private String code;

    @Column(name = "config_value", length = 512)
    private String value;

    String getCode() {
        return code;
    }

    void setCode(String code) {
        this.code = code;
    }

    String getValue() {
        return value;
    }

    void setValue(String value) {
        this.value = value;
    }
}
