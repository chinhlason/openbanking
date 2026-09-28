package vn.com.truongsonbank.shared.logging;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SensitiveDataMasker {
    private final List<Rule> rules;

    public SensitiveDataMasker(Map<String, LoggingProperties.MaskRule> maskRules) {
        this.rules = maskRules == null ? List.of() : maskRules.values().stream()
                .map(Rule::from)
                .filter(rule -> !rule.keys().isEmpty())
                .toList();
    }

    public String mask(String key, String value) {
        if (value == null) {
            return null;
        }
        if (key == null) {
            return value;
        }
        String normalized = key.toLowerCase();
        Rule matched = null;
        for (Rule rule : rules) {
            if (rule.keys().contains(normalized)) {
                matched = rule;
            }
        }
        return matched == null ? value : mask(value, matched);
    }

    private String mask(String value, Rule rule) {
        int keepFirst = Math.max(rule.keepFirst(), 0);
        int keepLast = Math.max(rule.keepLast(), 0);
        if (value.length() <= keepFirst + keepLast) {
            return rule.maskChar().repeat(value.length());
        }
        return value.substring(0, keepFirst)
                + rule.maskChar().repeat(value.length() - keepFirst - keepLast)
                + value.substring(value.length() - keepLast);
    }

    private record Rule(Set<String> keys, int keepFirst, int keepLast, String maskChar) {
        static Rule from(LoggingProperties.MaskRule rule) {
            String maskChar = rule.getMaskChar() == null || rule.getMaskChar().isBlank() ? "*" : rule.getMaskChar().substring(0, 1);
            Set<String> keys = List.of(rule.getKeys().split(",")).stream()
                    .map(String::trim)
                    .filter(key -> !key.isBlank())
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
            return new Rule(keys, rule.getKeepFirst(), rule.getKeepLast(), maskChar);
        }
    }
}
