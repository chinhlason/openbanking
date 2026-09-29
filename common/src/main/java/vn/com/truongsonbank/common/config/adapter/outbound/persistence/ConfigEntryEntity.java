package vn.com.truongsonbank.common.config.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import vn.com.truongsonbank.common.config.domain.model.ConfigEntry;
import vn.com.truongsonbank.common.config.domain.model.ConfigStatus;
import vn.com.truongsonbank.common.config.domain.model.ConfigValueType;
import vn.com.truongsonbank.shared.crypto.EncryptedEntity;
import vn.com.truongsonbank.shared.crypto.EncryptedField;

import java.time.Instant;

@Entity
@EncryptedEntity
@Table(name = "config_entry", indexes = {
        @Index(name = "idx_config_entry_lookup", columnList = "app,profile,status,version")
})
class ConfigEntryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 128)
    private String app;
    @Column(nullable = false, length = 128)
    private String profile;
    @Column(name = "config_key", nullable = false, length = 512)
    private String key;
    @EncryptedField
    @Column(name = "config_value", nullable = false, columnDefinition = "text")
    private String value;
    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 32)
    private ConfigValueType valueType;
    @Column(nullable = false)
    private long version;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConfigStatus status;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant publishedAt;

    static ConfigEntryEntity draft(String app, String profile, String key, String value, ConfigValueType valueType) {
        ConfigEntryEntity entity = new ConfigEntryEntity();
        entity.app = app;
        entity.profile = profile;
        entity.key = key;
        entity.value = value;
        entity.valueType = valueType;
        entity.version = 0;
        entity.status = ConfigStatus.DRAFT;
        return entity;
    }

    static ConfigEntryEntity published(ConfigEntryEntity draft, long version, Instant publishedAt) {
        ConfigEntryEntity entity = draft(draft.app, draft.profile, draft.key, draft.value, draft.valueType);
        entity.version = version;
        entity.status = ConfigStatus.PUBLISHED;
        entity.publishedAt = publishedAt;
        return entity;
    }

    ConfigEntry toDomain() {
        return new ConfigEntry(id, app, profile, key, value, valueType, version, status, createdAt, updatedAt, publishedAt);
    }

    String getKey() {
        return key;
    }

    String getApp() {
        return app;
    }

    String getProfile() {
        return profile;
    }

    String getValue() {
        return value;
    }

    ConfigValueType getValueType() {
        return valueType;
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
