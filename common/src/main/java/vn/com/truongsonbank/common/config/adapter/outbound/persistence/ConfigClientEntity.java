package vn.com.truongsonbank.common.config.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "config_client")
class ConfigClientEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 128)
    private String app;
    @Column(nullable = false, length = 256)
    private String apiKeyHash;
    @Column(nullable = false)
    private boolean enabled;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    static ConfigClientEntity enabled(String app, String apiKeyHash) {
        ConfigClientEntity entity = new ConfigClientEntity();
        entity.app = app;
        entity.apiKeyHash = apiKeyHash;
        entity.enabled = true;
        return entity;
    }

    void updateKey(String apiKeyHash) {
        this.apiKeyHash = apiKeyHash;
        this.enabled = true;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
