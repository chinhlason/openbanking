package vn.com.truongsonbank.shared.protocol;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpMethod;

class TsbProtocolPolicyResolver {
    private final ProtocolProperties properties;
    private final Map<String, List<AnnotatedOperation>> annotatedOperations = new ConcurrentHashMap<>();

    TsbProtocolPolicyResolver(ProtocolProperties properties) {
        this.properties = properties;
    }

    void register(String downstream, HttpMethod method, String path, TsbOperation annotation) {
        if (path == null || path.isBlank()) {
            return;
        }
        List<AnnotatedOperation> operations = annotatedOperations.computeIfAbsent(downstream, key -> new ArrayList<>());
        String normalizedPath = normalize(path);
        String operation = annotation == null || annotation.value().isBlank()
                ? (method == null ? normalizedPath : method.name() + " " + normalizedPath)
                : annotation.value();
        if (operations.stream().anyMatch(existing -> sameMapping(existing, method, normalizedPath))) {
            throw new IllegalStateException("Duplicate HTTP client operation mapping for downstream '"
                    + downstream + "': " + method + " " + normalizedPath);
        }
        operations.add(new AnnotatedOperation(method, normalizedPath, operation, policy(annotation)));
    }

    ResolvedProtocolPolicy resolve(String downstream, String operation) {
        ProtocolProperties.Policy defaults = properties.getDefaults();
        ProtocolProperties.Downstream downstreamPolicy = properties.getDownstreams().get(downstream);
        ProtocolProperties.Policy operationPolicy = annotatedPolicy(downstream, operation);
        if (downstreamPolicy != null && operation != null) {
            operationPolicy = merge(operationPolicy, downstreamPolicy.getOperations().get(operation));
        }
        return new ResolvedProtocolPolicy(downstream, operation, defaults, downstreamPolicy, operationPolicy);
    }

    String operationForRequest(String downstream, HttpMethod method, String path) {
        List<AnnotatedOperation> operations = annotatedOperations.getOrDefault(downstream, List.of());
        for (AnnotatedOperation operation : operations) {
            if ((operation.method() == null || operation.method().equals(method))
                    && matches(operation.path(), path)) {
                return operation.operation();
            }
        }
        return null;
    }

    String operationForPath(String downstream, String path) {
        return operationForRequest(downstream, null, path);
    }

    private ProtocolProperties.Policy annotatedPolicy(String downstream, String operation) {
        if (operation == null) {
            return null;
        }
        List<AnnotatedOperation> operations = annotatedOperations.getOrDefault(downstream, List.of());
        for (AnnotatedOperation annotated : operations) {
            if (operation.equals(annotated.operation())) {
                return annotated.policy();
            }
        }
        return null;
    }

    private ProtocolProperties.Policy policy(TsbOperation annotation) {
        if (annotation == null) {
            return null;
        }
        ProtocolProperties.Policy policy = new ProtocolProperties.Policy();
        policy.setResponseTimeout(duration(annotation.responseTimeout()));

        if (annotation.retryAttempts() >= 0 || !annotation.retryInitialDelay().isBlank()
                || !annotation.retryMaxDelay().isBlank() || !annotation.retryJitter()) {
            ProtocolProperties.Retry retry = new ProtocolProperties.Retry();
            if (annotation.retryAttempts() >= 0) {
                retry.setAttempts(annotation.retryAttempts());
            }
            ProtocolProperties.Backoff backoff = retry.getBackoff();
            Duration initialDelay = duration(annotation.retryInitialDelay());
            Duration maxDelay = duration(annotation.retryMaxDelay());
            if (initialDelay != null) {
                backoff.setInitialDelay(initialDelay);
            }
            if (maxDelay != null) {
                backoff.setMaxDelay(maxDelay);
            }
            backoff.setJitter(annotation.retryJitter());
            policy.setRetry(retry);
        }

        ProtocolProperties.CircuitBreaker circuitBreaker = new ProtocolProperties.CircuitBreaker();
        circuitBreaker.setEnabled(annotation.circuitBreakerEnabled());
        if (annotation.circuitBreakerMinimumCalls() >= 0) {
            circuitBreaker.setMinimumCalls(annotation.circuitBreakerMinimumCalls());
        }
        if (annotation.circuitBreakerSlidingWindowSize() >= 0) {
            circuitBreaker.setSlidingWindowSize(annotation.circuitBreakerSlidingWindowSize());
        }
        if (annotation.circuitBreakerFailureRateThreshold() >= 0) {
            circuitBreaker.setFailureRateThreshold(annotation.circuitBreakerFailureRateThreshold());
        }
        Duration openDuration = duration(annotation.circuitBreakerOpenDuration());
        if (openDuration != null) {
            circuitBreaker.setOpenDuration(openDuration);
        }
        if (annotation.circuitBreakerHalfOpenCalls() >= 0) {
            circuitBreaker.setHalfOpenCalls(annotation.circuitBreakerHalfOpenCalls());
        }
        policy.setCircuitBreaker(circuitBreaker);
        return policy;
    }

    private ProtocolProperties.Policy merge(ProtocolProperties.Policy first, ProtocolProperties.Policy second) {
        if (second == null) {
            return first;
        }
        if (first == null) {
            return second;
        }
        ProtocolProperties.Policy merged = new ProtocolProperties.Policy();
        merged.setResponseTimeout(second.getResponseTimeout() == null ? first.getResponseTimeout() : second.getResponseTimeout());
        merged.setRetry(second.getRetry() == null ? first.getRetry() : second.getRetry());
        merged.setCircuitBreaker(second.getCircuitBreaker() == null ? first.getCircuitBreaker() : second.getCircuitBreaker());
        merged.setLog(second.getLog() == null ? first.getLog() : second.getLog());
        merged.setServiceAuth(second.getServiceAuth() == null ? first.getServiceAuth() : second.getServiceAuth());
        return merged;
    }

    private Duration duration(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        if (normalized.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
        }
        if (normalized.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        return Duration.parse(value);
    }

    private boolean sameMapping(AnnotatedOperation existing, HttpMethod method, String path) {
        return (existing.method() == null ? method == null : existing.method().equals(method))
                && existing.path().equals(path);
    }

    private boolean matches(String template, String path) {
        String[] templateParts = normalize(template).split("/");
        String[] pathParts = normalize(path).split("/");
        if (templateParts.length != pathParts.length) {
            return false;
        }
        for (int i = 0; i < templateParts.length; i++) {
            String templatePart = templateParts[i];
            if (!templatePart.startsWith("{") && !templatePart.equals(pathParts[i])) {
                return false;
            }
        }
        return true;
    }

    private String normalize(String path) {
        String value = path == null ? "" : path.split("\\?", 2)[0].trim();
        if (value.isBlank()) {
            return "/";
        }
        value = value.replaceAll("/{2,}", "/");
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private record AnnotatedOperation(HttpMethod method, String path, String operation,
                                      ProtocolProperties.Policy policy) {
    }
}
