package vn.com.truongsonbank.common.config.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.truongsonbank.common.config.domain.model.ConfigErrors;
import vn.com.truongsonbank.common.config.domain.model.ConfigEntry;
import vn.com.truongsonbank.common.config.domain.model.ConfigPublishEvent;
import vn.com.truongsonbank.common.config.domain.model.ConfigSnapshot;
import vn.com.truongsonbank.common.config.domain.model.ConfigValueType;
import vn.com.truongsonbank.common.config.domain.port.ConfigEventPublisher;
import vn.com.truongsonbank.common.config.domain.port.ConfigRepository;
import vn.com.truongsonbank.shared.exception.TsbException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ConfigService {
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,128}$");
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,512}$");
    private static final int MAX_VALUE_LENGTH = 16_384;

    private final ConfigRepository repository;
    private final ConfigEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public ConfigService(ConfigRepository repository, ConfigEventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void saveDraft(String app, String profile, Map<String, Object> entries) {
        validateAppProfile(app, profile);
        if (entries == null || entries.isEmpty()) {
            throw new TsbException(ConfigErrors.ENTRIES_REQUIRED);
        }
        Map<String, ConfigRepository.TypedValue> values = new LinkedHashMap<>();
        entries.forEach((key, value) -> values.put(validateKey(key), typedValue(value)));
        repository.saveDraft(app, profile, values);
    }

    public List<ConfigEntry> draft(String app, String profile) {
        validateAppProfile(app, profile);
        return repository.draft(app, profile);
    }

    @Transactional
    public ConfigPublishEvent publish(String app, String profile) {
        validateAppProfile(app, profile);
        ConfigPublishEvent event = repository.publish(app, profile);
        eventPublisher.publish(event);
        return event;
    }

    @Transactional
    public ConfigPublishEvent publish(String app, String profile, List<String> keys) {
        validateAppProfile(app, profile);
        if (keys == null || keys.isEmpty()) {
            throw new TsbException(ConfigErrors.PUBLISH_KEYS_REQUIRED);
        }
        ConfigPublishEvent event = repository.publish(app, profile, keys.stream()
                .map(this::validateKey)
                .distinct()
                .toList());
        eventPublisher.publish(event);
        return event;
    }

    @Transactional
    public ConfigPublishEvent rollback(String app, String profile, long version) {
        validateAppProfile(app, profile);
        ConfigPublishEvent event = repository.rollback(app, profile, version);
        eventPublisher.publish(event);
        return event;
    }

    public List<ConfigRepository.AuditLog> audit(String app, String profile) {
        validateAppProfile(app, profile);
        return repository.audit(app, profile);
    }

    public ConfigSnapshot snapshot(String app, String profile, String apiKey) {
        validateAppProfile(app, profile);
        if (!repository.isClientAuthorized(app, apiKey)) {
            throw new TsbException(ConfigErrors.INVALID_API_KEY);
        }
        Map<String, String> flat = new LinkedHashMap<>();
        long version = 0;
        for (Scope scope : scopes(app, profile)) {
            for (ConfigEntry entry : repository.latestPublished(scope.app(), scope.profile())) {
                flat.put(entry.key(), entry.value());
                version = Math.max(version, entry.version());
            }
        }
        return new ConfigSnapshot(app, profile, version, flat, nested(flat));
    }

    private List<Scope> scopes(String app, String profile) {
        return List.of(
                new Scope("application", "default"),
                new Scope("application", profile),
                new Scope(app, "default"),
                new Scope(app, profile));
    }

    private String validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new TsbException(ConfigErrors.KEY_REQUIRED);
        }
        if (!KEY_PATTERN.matcher(key).matches() || key.contains("..")) {
            throw new TsbException(ConfigErrors.KEY_INVALID, key);
        }
        return key;
    }

    private void validateAppProfile(String app, String profile) {
        if (app == null || !NAME_PATTERN.matcher(app).matches()) {
            throw new TsbException(ConfigErrors.APP_INVALID);
        }
        if (profile == null || !NAME_PATTERN.matcher(profile).matches()) {
            throw new TsbException(ConfigErrors.PROFILE_INVALID);
        }
    }

    private ConfigRepository.TypedValue typedValue(Object value) {
        if (value == null) {
            throw new TsbException(ConfigErrors.VALUE_REQUIRED);
        }
        if (value instanceof Boolean) {
            return new ConfigRepository.TypedValue(String.valueOf(value), ConfigValueType.BOOLEAN);
        }
        if (value instanceof Number) {
            return new ConfigRepository.TypedValue(String.valueOf(value), ConfigValueType.NUMBER);
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            try {
                return typedString(objectMapper.writeValueAsString(value), ConfigValueType.JSON);
            } catch (JsonProcessingException ex) {
                throw new TsbException(ConfigErrors.JSON_VALUE_INVALID, ex);
            }
        }
        return typedString(String.valueOf(value), ConfigValueType.STRING);
    }

    private ConfigRepository.TypedValue typedString(String value, ConfigValueType type) {
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new TsbException(ConfigErrors.VALUE_TOO_LONG, MAX_VALUE_LENGTH);
        }
        return new ConfigRepository.TypedValue(value, type);
    }

    private Map<String, Object> nested(Map<String, String> flat) {
        Map<String, Object> root = new LinkedHashMap<>();
        flat.forEach((key, value) -> {
            String[] parts = key.split("\\.");
            Map<String, Object> cursor = root;
            for (int i = 0; i < parts.length - 1; i++) {
                cursor = (Map<String, Object>) cursor.computeIfAbsent(parts[i], ignored -> new LinkedHashMap<>());
            }
            cursor.put(parts[parts.length - 1], parseScalar(value));
        });
        return root;
    }

    private Object parseScalar(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private record Scope(String app, String profile) {
    }
}
