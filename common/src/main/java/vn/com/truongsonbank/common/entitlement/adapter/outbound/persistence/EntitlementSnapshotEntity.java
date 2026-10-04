package vn.com.truongsonbank.common.entitlement.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "entitlement_snapshot", indexes = {
        @Index(name = "idx_entitlement_snapshot_subject", columnList = "subject_type,subject_id", unique = true)
})
public class EntitlementSnapshotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "subject_type", nullable = false, length = 32)
    private String subjectType;
    @Column(name = "subject_id", nullable = false, length = 128)
    private String subjectId;
    @Column(name = "allow_operations", nullable = false, columnDefinition = "text")
    private String allowOperations = "";
    @Column(name = "deny_operations", nullable = false, columnDefinition = "text")
    private String denyOperations = "";
    @Column(nullable = false)
    private long version;
    @Column(nullable = false)
    private Instant expiresAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected EntitlementSnapshotEntity() {
    }

    public EntitlementSnapshotEntity(String subjectType, String subjectId, String allowOperations,
                                     String denyOperations, long version, Instant expiresAt) {
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.allowOperations = allowOperations;
        this.denyOperations = denyOperations;
        this.version = version;
        this.expiresAt = expiresAt;
        this.updatedAt = Instant.now();
    }

    public void update(String allowOperations, String denyOperations, long version, Instant expiresAt) {
        this.allowOperations = allowOperations;
        this.denyOperations = denyOperations;
        this.version = version;
        this.expiresAt = expiresAt;
        this.updatedAt = Instant.now();
    }

    public String getSubjectType() { return subjectType; }
    public String getSubjectId() { return subjectId; }
    public String getAllowOperations() { return allowOperations; }
    public String getDenyOperations() { return denyOperations; }
    public long getVersion() { return version; }
    public Instant getExpiresAt() { return expiresAt; }
}
