package vn.com.truongsonbank.common.config.domain.port;

import vn.com.truongsonbank.common.config.domain.model.ConfigEntry;
import vn.com.truongsonbank.common.config.domain.model.ConfigPublishEvent;
import vn.com.truongsonbank.common.config.domain.model.ConfigValueType;

import java.util.List;
import java.util.Map;

public interface ConfigRepository {
    void saveDraft(String app, String profile, Map<String, TypedValue> values);

    List<ConfigEntry> draft(String app, String profile);

    ConfigPublishEvent publish(String app, String profile);

    ConfigPublishEvent publish(String app, String profile, List<String> keys);

    ConfigPublishEvent rollback(String app, String profile, long version);

    List<ConfigEntry> latestPublished(String app, String profile);

    List<AuditLog> audit(String app, String profile);

    boolean isClientAuthorized(String app, String apiKey);

    void ensureClient(String app, String apiKey);

    record TypedValue(String value, ConfigValueType type) {
    }

    record AuditLog(Long id, String app, String profile, String action, long version, String keysJson, java.time.Instant createdAt) {
    }
}
