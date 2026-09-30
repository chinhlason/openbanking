package vn.com.truongsonbank.auth.login;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "auth_session")
class AuthSessionEntity {
    @Id
    private String sessionId;
    @Column(nullable = false)
    private String subject;
    private String username;
    private String deviceId;
    private String dpopJkt;
    private boolean trustedDevice;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant revokedAt;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDpopJkt() {
        return dpopJkt;
    }

    public void setDpopJkt(String dpopJkt) {
        this.dpopJkt = dpopJkt;
    }

    public boolean isTrustedDevice() {
        return trustedDevice;
    }

    public void setTrustedDevice(boolean trustedDevice) {
        this.trustedDevice = trustedDevice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
