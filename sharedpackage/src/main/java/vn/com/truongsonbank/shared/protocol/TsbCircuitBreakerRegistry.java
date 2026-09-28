package vn.com.truongsonbank.shared.protocol;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

class TsbCircuitBreakerRegistry {
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    CircuitBreaker get(ResolvedProtocolPolicy policy) {
        ProtocolProperties.CircuitBreaker config = policy.circuitBreaker();
        String name = policy.downstream() + ":" + policy.operation();
        return circuitBreakers.computeIfAbsent(name, ignored -> CircuitBreaker.of(name, CircuitBreakerConfig.custom()
                .minimumNumberOfCalls(config.getMinimumCalls())
                .slidingWindowSize(config.getSlidingWindowSize())
                .failureRateThreshold(config.getFailureRateThreshold())
                .waitDurationInOpenState(config.getOpenDuration())
                .permittedNumberOfCallsInHalfOpenState(config.getHalfOpenCalls())
                .build()));
    }
}
