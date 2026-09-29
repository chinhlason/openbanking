package vn.com.truongsonbank.client;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.truongsonbank.shared.response.ResponseWrapper;

import java.util.Map;

@RestController
class CommonConfigDemoController {
    private final CommonConfigClient configClient;

    CommonConfigDemoController(CommonConfigClient configClient) {
        this.configClient = configClient;
    }

    @ResponseWrapper
    @GetMapping("/shared-test/config/feature-transfer")
    Map<String, Object> featureTransfer() {
        CommonConfigSnapshot snapshot = configClient.snapshot();
        return Map.of(
                "version", snapshot == null ? 0 : snapshot.version(),
                "enabled", configClient.getBoolean("feature.transfer.enabled", false),
                "dailyLimit", configClient.getLong("limit.transfer.daily", 0),
                "theme", configClient.getString("ui.theme", "default"));
    }

    @ResponseWrapper
    @GetMapping("/shared-test/config/l1")
    Map<String, Object> getConfigL1(@RequestParam String key) {
        String config = configClient.getString(key, "defaultVal");
        return Map.of("key", key, "value", config);
    }

    @ResponseWrapper
    @GetMapping("/shared-test/config/snapshot")
    Map<String, Object> snapshot() {
        CommonConfigSnapshot snapshot = configClient.snapshot();
        if (snapshot == null) {
            return Map.of("loaded", false);
        }
        return Map.of(
                "loaded", true,
                "app", snapshot.app(),
                "profile", snapshot.profile(),
                "version", snapshot.version(),
                "flat", snapshot.flat(),
                "nested", snapshot.nested());
    }

    @ResponseWrapper
    @GetMapping("/shared-test/config/stacktrace")
    Map<String, Object> stacktrace() {
        throw new IllegalStateException("Loki stacktrace test from client config demo");
    }

    @ResponseWrapper
    @PostMapping("/shared-test/config/reload")
    Map<String, Object> reload() {
        configClient.reload("manual", false);
        CommonConfigSnapshot snapshot = configClient.snapshot();
        return Map.of("version", snapshot == null ? 0 : snapshot.version());
    }
}
