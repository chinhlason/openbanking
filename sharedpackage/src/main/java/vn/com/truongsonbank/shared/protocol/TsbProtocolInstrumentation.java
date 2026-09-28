package vn.com.truongsonbank.shared.protocol;

import java.time.Duration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

class TsbProtocolInstrumentation {
    private final MeterRegistry meterRegistry;

    TsbProtocolInstrumentation(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void recordDuration(String downstream, String operation, String method, String outcome, String status, Duration duration) {
        Timer.builder("tsb.protocol.outbound.duration")
                .description("Duration of outbound protocol calls")
                .tag("protocol", "http")
                .tag("downstream", downstream)
                .tag("operation", operation)
                .tag("method", method)
                .tag("outcome", outcome)
                .tag("status", status)
                .register(meterRegistry)
                .record(duration);
    }

    void count(String metric, String downstream, String operation, String outcome) {
        meterRegistry.counter("tsb.protocol." + metric,
                "protocol", "http",
                "downstream", downstream,
                "operation", operation,
                "outcome", outcome).increment();
    }
}
