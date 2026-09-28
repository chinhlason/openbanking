package vn.com.truongsonbank.shared.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tsb.shared.logging")
public class LoggingProperties {
    private boolean enabled = true;
    private Request request = new Request();
    private Map<String, MaskRule> maskRules = defaultMaskRules();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Request getRequest() {
        return request;
    }

    public Map<String, MaskRule> getMaskRules() {
        return maskRules;
    }

    public void setMaskRules(Map<String, MaskRule> maskRules) {
        this.maskRules = maskRules == null ? new LinkedHashMap<>() : new LinkedHashMap<>(maskRules);
    }

    private static Map<String, MaskRule> defaultMaskRules() {
        Map<String, MaskRule> rules = new LinkedHashMap<>();
        rules.put("secret", new MaskRule("password,pin,otp,token,authorization,cccd", 0, 0, "*"));
        rules.put("phone", new MaskRule("phone,phoneNumber,mobile,sdt", 0, 3, "*"));
        return rules;
    }

    public static class Request {
        private boolean enabled = true;
        private boolean includeHeaders;
        private boolean includeQueryString = true;
        private boolean includeBody;
        private boolean healthcheckLogEnabled;
        private int maxBodyLength = 4096;
        private List<String> excludePaths = new ArrayList<>(List.of(
                "/actuator/prometheus",
                "/actuator/health",
                "/actuator/health/**",
                "/actuator/metrics",
                "/actuator/metrics/**"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isIncludeHeaders() {
            return includeHeaders;
        }

        public void setIncludeHeaders(boolean includeHeaders) {
            this.includeHeaders = includeHeaders;
        }

        public boolean isIncludeQueryString() {
            return includeQueryString;
        }

        public void setIncludeQueryString(boolean includeQueryString) {
            this.includeQueryString = includeQueryString;
        }

        public boolean isIncludeBody() {
            return includeBody;
        }

        public void setIncludeBody(boolean includeBody) {
            this.includeBody = includeBody;
        }

        public boolean isHealthcheckLogEnabled() {
            return healthcheckLogEnabled;
        }

        public void setHealthcheckLogEnabled(boolean healthcheckLogEnabled) {
            this.healthcheckLogEnabled = healthcheckLogEnabled;
        }

        public int getMaxBodyLength() {
            return maxBodyLength;
        }

        public void setMaxBodyLength(int maxBodyLength) {
            this.maxBodyLength = maxBodyLength;
        }

        public List<String> getExcludePaths() {
            return excludePaths;
        }

        public void setExcludePaths(List<String> excludePaths) {
            this.excludePaths = excludePaths == null ? new ArrayList<>() : new ArrayList<>(excludePaths);
        }
    }

    public static class MaskRule {
        private String keys = "";
        private int keepFirst;
        private int keepLast;
        private String maskChar = "*";

        public MaskRule() {
        }

        public MaskRule(String keys, int keepFirst, int keepLast, String maskChar) {
            this.keys = keys;
            this.keepFirst = keepFirst;
            this.keepLast = keepLast;
            this.maskChar = maskChar;
        }

        public String getKeys() {
            return keys;
        }

        public void setKeys(String keys) {
            this.keys = keys;
        }

        public int getKeepFirst() {
            return keepFirst;
        }

        public void setKeepFirst(int keepFirst) {
            this.keepFirst = keepFirst;
        }

        public int getKeepLast() {
            return keepLast;
        }

        public void setKeepLast(int keepLast) {
            this.keepLast = keepLast;
        }

        public String getMaskChar() {
            return maskChar;
        }

        public void setMaskChar(String maskChar) {
            this.maskChar = maskChar;
        }
    }
}
