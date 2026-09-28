package vn.com.truongsonbank.common.config.adapter.inbound.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import vn.com.truongsonbank.common.config.application.dto.PublishConfigRequest;
import vn.com.truongsonbank.common.config.application.dto.SaveDraftRequest;
import vn.com.truongsonbank.common.config.application.usecase.ConfigService;
import vn.com.truongsonbank.common.config.domain.model.ConfigErrors;
import vn.com.truongsonbank.common.config.domain.model.ConfigEntry;
import vn.com.truongsonbank.common.config.domain.model.ConfigPublishEvent;
import vn.com.truongsonbank.common.config.domain.model.ConfigSnapshot;
import vn.com.truongsonbank.common.config.infrastructure.config.ConfigServerProperties;
import vn.com.truongsonbank.shared.exception.TsbException;
import vn.com.truongsonbank.shared.response.ResponseWrapper;

import java.util.List;
import java.util.Map;

@RestController
@ResponseWrapper
public class ConfigController {
    private final ConfigService configService;
    private final ConfigServerProperties properties;

    public ConfigController(ConfigService configService, ConfigServerProperties properties) {
        this.configService = configService;
        this.properties = properties;
    }

    @GetMapping("/config/v1/apps/{app}/profiles/{profile}")
    ConfigSnapshot snapshot(@PathVariable String app,
                            @PathVariable String profile,
                            @RequestHeader("X-Config-Api-Key") String apiKey) {
        return configService.snapshot(app, profile, apiKey);
    }

    @GetMapping("/config/v1/apps/{app}/profiles/{profile}/versions/latest")
    ConfigSnapshot latest(@PathVariable String app,
                          @PathVariable String profile,
                          @RequestHeader("X-Config-Api-Key") String apiKey) {
        return configService.snapshot(app, profile, apiKey);
    }

    @PostMapping("/config/v1/apps/{app}/profiles/{profile}/draft")
    Map<String, Object> saveDraft(@PathVariable String app,
                                  @PathVariable String profile,
                                  @RequestHeader("X-Config-Admin-Key") String adminKey,
                                  @RequestBody SaveDraftRequest request) {
        assertAdmin(adminKey);
        configService.saveDraft(app, profile, request.entries());
        return Map.of("app", app, "profile", profile, "status", "DRAFT");
    }

    @GetMapping("/config/v1/apps/{app}/profiles/{profile}/draft")
    List<ConfigEntry> draft(@PathVariable String app,
                            @PathVariable String profile,
                            @RequestHeader("X-Config-Admin-Key") String adminKey) {
        assertAdmin(adminKey);
        return configService.draft(app, profile);
    }

    @PostMapping("/config/v1/apps/{app}/profiles/{profile}/publish")
    ConfigPublishEvent publish(@PathVariable String app,
                               @PathVariable String profile,
                               @RequestHeader("X-Config-Admin-Key") String adminKey) {
        assertAdmin(adminKey);
        return configService.publish(app, profile);
    }

    @PostMapping("/config/v1/apps/{app}/profiles/{profile}/publish/keys")
    ConfigPublishEvent publishKeys(@PathVariable String app,
                                   @PathVariable String profile,
                                   @RequestHeader("X-Config-Admin-Key") String adminKey,
                                   @RequestBody PublishConfigRequest request) {
        assertAdmin(adminKey);
        return configService.publish(app, profile, request.keys());
    }

    private void assertAdmin(String adminKey) {
        if (adminKey == null || !adminKey.equals(properties.getAdminApiKey())) {
            throw new TsbException(ConfigErrors.INVALID_ADMIN_KEY);
        }
    }
}
