package vn.com.truongsonbank.shared.protocol;

import java.time.Duration;

class ResolvedProtocolPolicy {
    private final String downstream;
    private final String operation;
    private final ProtocolProperties.Policy defaults;
    private final ProtocolProperties.Policy downstreamPolicy;
    private final ProtocolProperties.Policy operationPolicy;

    ResolvedProtocolPolicy(
            String downstream,
            String operation,
            ProtocolProperties.Policy defaults,
            ProtocolProperties.Policy downstreamPolicy,
            ProtocolProperties.Policy operationPolicy) {
        this.downstream = downstream;
        this.operation = operation;
        this.defaults = defaults;
        this.downstreamPolicy = downstreamPolicy;
        this.operationPolicy = operationPolicy;
    }

    String downstream() {
        return downstream;
    }

    String operation() {
        return operation;
    }

    Duration responseTimeout() {
        return duration(ProtocolProperties.Policy::getResponseTimeout);
    }

    ProtocolProperties.Retry retry() {
        return object(ProtocolProperties.Policy::getRetry, new ProtocolProperties.Retry());
    }

    ProtocolProperties.CircuitBreaker circuitBreaker() {
        return object(ProtocolProperties.Policy::getCircuitBreaker, new ProtocolProperties.CircuitBreaker());
    }

    ProtocolProperties.OutboundLog log() {
        return object(ProtocolProperties.Policy::getLog, new ProtocolProperties.OutboundLog());
    }

    private Duration duration(ValueGetter<Duration> getter) {
        Duration value = operationPolicy == null ? null : getter.get(operationPolicy);
        if (value != null) {
            return value;
        }
        value = downstreamPolicy == null ? null : getter.get(downstreamPolicy);
        if (value != null) {
            return value;
        }
        return getter.get(defaults);
    }

    private <T> T object(ValueGetter<T> getter, T fallback) {
        T value = operationPolicy == null ? null : getter.get(operationPolicy);
        if (value != null) {
            return value;
        }
        value = downstreamPolicy == null ? null : getter.get(downstreamPolicy);
        if (value != null) {
            return value;
        }
        value = getter.get(defaults);
        return value == null ? fallback : value;
    }

    private interface ValueGetter<T> {
        T get(ProtocolProperties.Policy policy);
    }
}
