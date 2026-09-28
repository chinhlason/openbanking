package vn.com.truongsonbank.shared.cache;

import java.util.concurrent.Callable;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;

class CacheInstrumentation {
    private final ObjectProvider<MeterRegistry> meterRegistry;
    private final ObjectProvider<Tracer> tracer;

    CacheInstrumentation(ObjectProvider<MeterRegistry> meterRegistry, ObjectProvider<Tracer> tracer) {
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
    }

    <T> T record(String operation, String cacheName, Callable<T> action) throws Throwable {
        long start = System.nanoTime();
        Span span = startSpan(operation, cacheName);
        String outcome = "success";
        try (Tracer.SpanInScope ignored = spanInScope(span)) {
            return action.call();
        } catch (Throwable error) {
            outcome = "error";
            tag(span, "error", error.getClass().getSimpleName());
            throw error;
        } finally {
            tag(span, "outcome", outcome);
            end(span);
            recordTimer(operation, cacheName, outcome, System.nanoTime() - start);
        }
    }

    private Span startSpan(String operation, String cacheName) {
        Tracer currentTracer = tracer.getIfAvailable();
        if (currentTracer == null) {
            return null;
        }
        Span span = currentTracer.nextSpan().name("tsb.cache." + operation).start();
        tag(span, "cache.name", cacheName);
        tag(span, "cache.operation", operation);
        return span;
    }

    private Tracer.SpanInScope spanInScope(Span span) {
        Tracer currentTracer = tracer.getIfAvailable();
        if (currentTracer == null || span == null) {
            return () -> {
            };
        }
        return currentTracer.withSpan(span);
    }

    private void tag(Span span, String key, String value) {
        if (span != null) {
            span.tag(key, value);
        }
    }

    private void end(Span span) {
        if (span != null) {
            span.end();
        }
    }

    private void recordTimer(String operation, String cacheName, String outcome, long nanos) {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        if (registry == null) {
            return;
        }
        Timer.builder("tsb.cache.operation.duration")
                .description("Duration of shared cache framework operations")
                .tag("operation", operation)
                .tag("cache", cacheName)
                .tag("outcome", outcome)
                .register(registry)
                .record(nanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }
}
