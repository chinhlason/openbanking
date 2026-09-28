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

    List<ConfigEntry> latestPublished(String app, String profile);

    boolean isClientAuthorized(String app, String apiKey);

    void ensureClient(String app, String apiKey);

    record TypedValue(String value, ConfigValueType type) {
    }
}
