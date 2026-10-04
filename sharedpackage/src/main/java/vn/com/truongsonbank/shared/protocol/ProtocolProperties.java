package vn.com.truongsonbank.shared.protocol;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tsb.shared.protocol")
public class ProtocolProperties {
    private boolean enabled = true;
    private Discovery discovery = new Discovery();
    private Policy defaults = Policy.defaults();
    private Map<String, Downstream> downstreams = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Discovery getDiscovery() {
        return discovery;
    }

    public void setDiscovery(Discovery discovery) {
        this.discovery = discovery == null ? new Discovery() : discovery;
    }

    public Policy getDefaults() {
        return defaults;
    }

    public void setDefaults(Policy defaults) {
        this.defaults = defaults == null ? Policy.defaults() : Policy.mergeDefaults(defaults);
    }

    public Map<String, Downstream> getDownstreams() {
        return downstreams;
    }

    public void setDownstreams(Map<String, Downstream> downstreams) {
        this.downstreams = downstreams == null ? new LinkedHashMap<>() : new LinkedHashMap<>(downstreams);
    }

    public static class Downstream extends Policy {
        private boolean enabled = true;
        private String baseUrl;
        private String target;
        private String serviceId;
        private String contextPath;
        private String protocol = "http";
        private Map<String, Policy> operations = new LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }

        public String getContextPath() {
            return contextPath;
        }

        public void setContextPath(String contextPath) {
            this.contextPath = contextPath;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public Map<String, Policy> getOperations() {
            return operations;
        }

        public void setOperations(Map<String, Policy> operations) {
            this.operations = operations == null ? new LinkedHashMap<>() : new LinkedHashMap<>(operations);
        }
    }

    public static class Discovery {
        private boolean enabled;
        private String provider = "consul";
        private String consulUrl = "http://localhost:8500";
        private Registration registration = new Registration();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getConsulUrl() {
            return consulUrl;
        }

        public void setConsulUrl(String consulUrl) {
            this.consulUrl = consulUrl;
        }

        public Registration getRegistration() {
            return registration;
        }

        public void setRegistration(Registration registration) {
            this.registration = registration == null ? new Registration() : registration;
        }
    }

    public static class Registration {
        private boolean enabled;
        private String serviceId;
        private String serviceName;
        private String address;
        private int port;
        private String healthPath = "/actuator/health";
        private String interval = "10s";
        private String timeout = "2s";
        private Grpc grpc = new Grpc();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getHealthPath() {
            return healthPath;
        }

        public void setHealthPath(String healthPath) {
            this.healthPath = healthPath;
        }

        public String getInterval() {
            return interval;
        }

        public void setInterval(String interval) {
            this.interval = interval;
        }

        public String getTimeout() {
            return timeout;
        }

        public void setTimeout(String timeout) {
            this.timeout = timeout;
        }

        public Grpc getGrpc() {
            return grpc;
        }

        public void setGrpc(Grpc grpc) {
            this.grpc = grpc == null ? new Grpc() : grpc;
        }
    }

    public static class Grpc {
        private boolean enabled;
        private String serviceId;
        private String serviceName;
        private int port;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    public static class Policy {
        private Duration connectTimeout;
        private Duration responseTimeout;
        private Retry retry;
        private CircuitBreaker circuitBreaker;
        private OutboundLog log;
        private ServiceAuth serviceAuth;

        static Policy defaults() {
            Policy policy = new Policy();
            policy.setConnectTimeout(Duration.ofMillis(500));
            policy.setResponseTimeout(Duration.ofSeconds(2));
            policy.setRetry(new Retry());
            policy.setCircuitBreaker(new CircuitBreaker());
            policy.setLog(new OutboundLog());
            policy.setServiceAuth(new ServiceAuth());
            return policy;
        }

        static Policy mergeDefaults(Policy configured) {
            Policy fallback = defaults();
            if (configured.getConnectTimeout() == null) {
                configured.setConnectTimeout(fallback.getConnectTimeout());
            }
            if (configured.getResponseTimeout() == null) {
                configured.setResponseTimeout(fallback.getResponseTimeout());
            }
            if (configured.getRetry() == null) {
                configured.setRetry(fallback.getRetry());
            }
            if (configured.getCircuitBreaker() == null) {
                configured.setCircuitBreaker(fallback.getCircuitBreaker());
            }
            if (configured.getLog() == null) {
                configured.setLog(fallback.getLog());
            }
            if (configured.getServiceAuth() == null) {
                configured.setServiceAuth(fallback.getServiceAuth());
            }
            return configured;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getResponseTimeout() {
            return responseTimeout;
        }

        public void setResponseTimeout(Duration responseTimeout) {
            this.responseTimeout = responseTimeout;
        }

        public Retry getRetry() {
            return retry;
        }

        public void setRetry(Retry retry) {
            this.retry = retry == null ? new Retry() : retry;
        }

        public CircuitBreaker getCircuitBreaker() {
            return circuitBreaker;
        }

        public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker == null ? new CircuitBreaker() : circuitBreaker;
        }

        public OutboundLog getLog() {
            return log;
        }

        public void setLog(OutboundLog log) {
            this.log = log == null ? new OutboundLog() : log;
        }

        public ServiceAuth getServiceAuth() {
            return serviceAuth;
        }

        public void setServiceAuth(ServiceAuth serviceAuth) {
            this.serviceAuth = serviceAuth == null ? new ServiceAuth() : serviceAuth;
        }
    }

    public static class ServiceAuth {
        private boolean enabled;
        private String clientId;
        private String clientSecret;
        private String tokenUri;
        private String audience;
        private Duration tokenRefreshSkew = Duration.ofSeconds(30);
        private boolean retryOnUnauthorizedOnce = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public String getTokenUri() { return tokenUri; }
        public void setTokenUri(String tokenUri) { this.tokenUri = tokenUri; }
        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience = audience; }
        public Duration getTokenRefreshSkew() { return tokenRefreshSkew; }
        public void setTokenRefreshSkew(Duration tokenRefreshSkew) { this.tokenRefreshSkew = tokenRefreshSkew; }
        public boolean isRetryOnUnauthorizedOnce() { return retryOnUnauthorizedOnce; }
        public void setRetryOnUnauthorizedOnce(boolean retryOnUnauthorizedOnce) { this.retryOnUnauthorizedOnce = retryOnUnauthorizedOnce; }
    }

    public static class Retry {
        private int attempts;
        private Backoff backoff = new Backoff();
        private Duration maxRetryAfter = Duration.ofSeconds(5);
        private boolean requireIdempotencyForUnsafeMethods = true;

        public int getAttempts() {
            return attempts;
        }

        public void setAttempts(int attempts) {
            this.attempts = attempts;
        }

        public Backoff getBackoff() {
            return backoff;
        }

        public void setBackoff(Backoff backoff) {
            this.backoff = backoff == null ? new Backoff() : backoff;
        }

        public Duration getMaxRetryAfter() {
            return maxRetryAfter;
        }

        public void setMaxRetryAfter(Duration maxRetryAfter) {
            this.maxRetryAfter = maxRetryAfter;
        }

        public boolean isRequireIdempotencyForUnsafeMethods() {
            return requireIdempotencyForUnsafeMethods;
        }

        public void setRequireIdempotencyForUnsafeMethods(boolean requireIdempotencyForUnsafeMethods) {
            this.requireIdempotencyForUnsafeMethods = requireIdempotencyForUnsafeMethods;
        }
    }

    public static class Backoff {
        private String strategy = "exponential";
        private Duration initialDelay = Duration.ofMillis(100);
        private Duration maxDelay = Duration.ofSeconds(1);
        private boolean jitter = true;

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public Duration getInitialDelay() {
            return initialDelay;
        }

        public void setInitialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
        }

        public Duration getMaxDelay() {
            return maxDelay;
        }

        public void setMaxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
        }

        public boolean isJitter() {
            return jitter;
        }

        public void setJitter(boolean jitter) {
            this.jitter = jitter;
        }
    }

    public static class CircuitBreaker {
        private boolean enabled = true;
        private int minimumCalls = 20;
        private int slidingWindowSize = 50;
        private float failureRateThreshold = 60;
        private Duration openDuration = Duration.ofSeconds(30);
        private int halfOpenCalls = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMinimumCalls() {
            return minimumCalls;
        }

        public void setMinimumCalls(int minimumCalls) {
            this.minimumCalls = minimumCalls;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public Duration getOpenDuration() {
            return openDuration;
        }

        public void setOpenDuration(Duration openDuration) {
            this.openDuration = openDuration;
        }

        public int getHalfOpenCalls() {
            return halfOpenCalls;
        }

        public void setHalfOpenCalls(int halfOpenCalls) {
            this.halfOpenCalls = halfOpenCalls;
        }
    }

    public static class OutboundLog {
        private boolean enabled;
        private boolean logFailures = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isLogFailures() {
            return logFailures;
        }

        public void setLogFailures(boolean logFailures) {
            this.logFailures = logFailures;
        }
    }
}
