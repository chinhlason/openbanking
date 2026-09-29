package vn.com.truongsonbank.common.config.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "config_audit_log", indexes = {
        @Index(name = "idx_config_audit_lookup", columnList = "app,profile,createdAt")
})
class ConfigAuditLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 128)
    private String app;
    @Column(nullable = false, length = 128)
    private String profile;
    @Column(nullable = false, length = 64)
    private String action;
    @Column(nullable = false)
    private long version;
    @Column(columnDefinition = "text")
    private String keysJson;
    @Column(nullable = false)
    private Instant createdAt;

    ConfigAuditLogEntity() {
    }

    ConfigAuditLogEntity(String app, String profile, String action, long version, String keysJson) {
        this.app = app;
        this.profile = profile;
        this.action = action;
        this.version = version;
        this.keysJson = keysJson;
        this.createdAt = Instant.now();
    }

    Long getId() {
        return id;
    }

    String getApp() {
        return app;
    }

    String getProfile() {
        return profile;
    }

    String getAction() {
        return action;
    }

    long getVersion() {
        return version;
    }

    String getKeysJson() {
        return keysJson;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
