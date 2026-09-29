package vn.com.truongsonbank.common.config.adapter.outbound.persistence;

import org.springframework.stereotype.Repository;
import vn.com.truongsonbank.common.config.domain.model.ConfigErrors;
import vn.com.truongsonbank.common.config.domain.model.ConfigEntry;
import vn.com.truongsonbank.common.config.domain.model.ConfigPublishEvent;
import vn.com.truongsonbank.common.config.domain.model.ConfigStatus;
import vn.com.truongsonbank.common.config.domain.port.ConfigRepository;
import vn.com.truongsonbank.shared.exception.TsbException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Repository
class JpaConfigRepository implements ConfigRepository {
    private final SpringDataConfigEntryRepository entries;
    private final SpringDataConfigClientRepository clients;
    private final SpringDataConfigPublishEventRepository events;
    private final SpringDataConfigAuditLogRepository auditLogs;

    JpaConfigRepository(SpringDataConfigEntryRepository entries,
                        SpringDataConfigClientRepository clients,
                        SpringDataConfigPublishEventRepository events,
                        SpringDataConfigAuditLogRepository auditLogs) {
        this.entries = entries;
        this.clients = clients;
        this.events = events;
        this.auditLogs = auditLogs;
    }

    @Override
    public void saveDraft(String app, String profile, Map<String, TypedValue> values) {
        entries.deleteByAppAndProfileAndStatus(app, profile, ConfigStatus.DRAFT);
        entries.saveAll(values.entrySet().stream()
                .map(entry -> ConfigEntryEntity.draft(app, profile, entry.getKey(), entry.getValue().value(), entry.getValue().type()))
                .toList());
        auditLogs.save(new ConfigAuditLogEntity(app, profile, "SAVE_DRAFT", 0, jsonArray(values.keySet().stream().toList())));
    }

    @Override
    public List<ConfigEntry> draft(String app, String profile) {
        return entries.findByAppAndProfileAndStatusOrderByKey(app, profile, ConfigStatus.DRAFT)
                .stream()
                .map(ConfigEntryEntity::toDomain)
                .toList();
    }

    @Override
    public ConfigPublishEvent publish(String app, String profile) {
        List<ConfigEntryEntity> drafts = entries.findByAppAndProfileAndStatusOrderByKey(app, profile, ConfigStatus.DRAFT);
        return publishDrafts(app, profile, drafts, true);
    }

    @Override
    public ConfigPublishEvent publish(String app, String profile, List<String> keys) {
        List<ConfigEntryEntity> drafts = entries.findByAppAndProfileAndStatusAndKeyInOrderByKey(
                app, profile, ConfigStatus.DRAFT, keys);
        return publishDrafts(app, profile, drafts, false);
    }

    private ConfigPublishEvent publishDrafts(String app, String profile, List<ConfigEntryEntity> drafts, boolean deleteAllDrafts) {
        if (drafts.isEmpty()) {
            throw new TsbException(ConfigErrors.DRAFT_NOT_FOUND);
        }
        long nextVersion = entries.maxVersion(app, profile, ConfigStatus.PUBLISHED) + 1;
        Instant publishedAt = Instant.now();
        Map<String, ConfigEntryEntity> nextSnapshot = new LinkedHashMap<>();
        latestPublishedEntities(app, profile).forEach(entry -> nextSnapshot.put(entry.getKey(), entry));
        drafts.forEach(draft -> nextSnapshot.put(draft.getKey(), draft));
        entries.saveAll(nextSnapshot.values().stream()
                .map(entry -> ConfigEntryEntity.published(entry, nextVersion, publishedAt))
                .toList());
        List<String> keys = drafts.stream().map(ConfigEntryEntity::getKey).toList();
        if (deleteAllDrafts) {
            entries.deleteByAppAndProfileAndStatus(app, profile, ConfigStatus.DRAFT);
        } else {
            entries.deleteByAppAndProfileAndStatusAndKeyIn(app, profile, ConfigStatus.DRAFT, keys);
        }
        events.save(new ConfigPublishEventEntity(app, profile, nextVersion, jsonArray(keys), publishedAt));
        auditLogs.save(new ConfigAuditLogEntity(app, profile, "PUBLISH", nextVersion, jsonArray(keys)));
        return new ConfigPublishEvent(app, profile, nextVersion, keys, publishedAt);
    }

    @Override
    public ConfigPublishEvent rollback(String app, String profile, long version) {
        List<ConfigEntryEntity> oldSnapshot = entries.findByAppAndProfileAndStatusAndVersionOrderByKey(
                app, profile, ConfigStatus.PUBLISHED, version);
        if (oldSnapshot.isEmpty()) {
            throw new TsbException(ConfigErrors.VERSION_NOT_FOUND, version);
        }
        long nextVersion = entries.maxVersion(app, profile, ConfigStatus.PUBLISHED) + 1;
        Instant publishedAt = Instant.now();
        entries.saveAll(oldSnapshot.stream()
                .map(entry -> ConfigEntryEntity.published(entry, nextVersion, publishedAt))
                .toList());
        List<String> keys = oldSnapshot.stream().map(ConfigEntryEntity::getKey).toList();
        events.save(new ConfigPublishEventEntity(app, profile, nextVersion, jsonArray(keys), publishedAt));
        auditLogs.save(new ConfigAuditLogEntity(app, profile, "ROLLBACK", nextVersion, jsonArray(keys)));
        return new ConfigPublishEvent(app, profile, nextVersion, keys, publishedAt);
    }

    @Override
    public List<ConfigEntry> latestPublished(String app, String profile) {
        long version = entries.maxVersion(app, profile, ConfigStatus.PUBLISHED);
        if (version == 0) {
            return List.of();
        }
        return latestPublishedEntities(app, profile)
                .stream()
                .map(ConfigEntryEntity::toDomain)
                .toList();
    }

    @Override
    public List<AuditLog> audit(String app, String profile) {
        return auditLogs.findTop100ByAppAndProfileOrderByCreatedAtDesc(app, profile)
                .stream()
                .map(log -> new AuditLog(log.getId(), log.getApp(), log.getProfile(), log.getAction(),
                        log.getVersion(), log.getKeysJson(), log.getCreatedAt()))
                .toList();
    }

    private List<ConfigEntryEntity> latestPublishedEntities(String app, String profile) {
        long version = entries.maxVersion(app, profile, ConfigStatus.PUBLISHED);
        if (version == 0) {
            return List.of();
        }
        return entries.findByAppAndProfileAndStatusAndVersionOrderByKey(app, profile, ConfigStatus.PUBLISHED, version);
    }

    @Override
    public boolean isClientAuthorized(String app, String apiKey) {
        return apiKey != null && !apiKey.isBlank()
                && clients.existsByAppAndApiKeyHashAndEnabledTrue(app, sha256(apiKey));
    }

    @Override
    public void ensureClient(String app, String apiKey) {
        if (app == null || app.isBlank() || apiKey == null || apiKey.isBlank()) {
            return;
        }
        String hash = sha256(apiKey);
        ConfigClientEntity client = clients.findByApp(app)
                .orElseGet(() -> ConfigClientEntity.enabled(app, hash));
        client.updateKey(hash);
        clients.save(client);
    }

    private String jsonArray(List<String> values) {
        return "[\"" + String.join("\",\"", values) + "\"]";
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash API key", ex);
        }
    }
}
