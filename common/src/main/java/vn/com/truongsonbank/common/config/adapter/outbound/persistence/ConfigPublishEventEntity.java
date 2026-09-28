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
@Table(name = "config_publish_event", indexes = {
        @Index(name = "idx_config_publish_event_lookup", columnList = "app,profile,version")
})
class ConfigPublishEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 128)
    private String app;
    @Column(nullable = false, length = 128)
    private String profile;
    @Column(nullable = false)
    private long version;
    @Column(nullable = false, columnDefinition = "text")
    private String changedKeysJson;
    @Column(nullable = false)
    private Instant publishedAt;

    ConfigPublishEventEntity() {
    }

    ConfigPublishEventEntity(String app, String profile, long version, String changedKeysJson, Instant publishedAt) {
        this.app = app;
        this.profile = profile;
        this.version = version;
        this.changedKeysJson = changedKeysJson;
        this.publishedAt = publishedAt;
    }
}
