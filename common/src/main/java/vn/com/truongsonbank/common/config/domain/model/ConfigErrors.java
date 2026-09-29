package vn.com.truongsonbank.common.config.domain.model;

import vn.com.truongsonbank.shared.exception.ErrorDescriptor;

public enum ConfigErrors implements ErrorDescriptor {
    INVALID_API_KEY("CONFIG_INVALID_API_KEY", 401, "Invalid config API key"),
    INVALID_ADMIN_KEY("CONFIG_INVALID_ADMIN_KEY", 401, "Invalid config admin API key"),
    ENTRIES_REQUIRED("CONFIG_ENTRIES_REQUIRED", 400, "Config entries are required"),
    KEY_REQUIRED("CONFIG_KEY_REQUIRED", 400, "Config key is required"),
    VALUE_REQUIRED("CONFIG_VALUE_REQUIRED", 400, "Config value is required"),
    JSON_VALUE_INVALID("CONFIG_JSON_VALUE_INVALID", 400, "JSON config value is invalid"),
    PUBLISH_KEYS_REQUIRED("CONFIG_PUBLISH_KEYS_REQUIRED", 400, "Config publish keys are required"),
    DRAFT_NOT_FOUND("CONFIG_DRAFT_NOT_FOUND", 400, "No draft config to publish"),
    VERSION_NOT_FOUND("CONFIG_VERSION_NOT_FOUND", 404, "Config version not found: {0}"),
    APP_INVALID("CONFIG_APP_INVALID", 400, "Config app is invalid"),
    PROFILE_INVALID("CONFIG_PROFILE_INVALID", 400, "Config profile is invalid"),
    KEY_INVALID("CONFIG_KEY_INVALID", 400, "Config key is invalid: {0}"),
    VALUE_TOO_LONG("CONFIG_VALUE_TOO_LONG", 400, "Config value is too long: {0}");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    ConfigErrors(String code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
