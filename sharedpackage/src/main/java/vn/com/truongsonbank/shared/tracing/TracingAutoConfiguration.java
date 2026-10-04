package vn.com.truongsonbank.shared.tracing;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.server.observation.ServerRequestObservationContext;

@AutoConfiguration(beforeName = {
        "org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration",
        "org.springframework.boot.micrometer.tracing.autoconfigure.NoopTracerAutoConfiguration"
})
@ConditionalOnClass({OtelTracer.class, OpenTelemetrySdk.class})
@EnableConfigurationProperties(TracingProperties.class)
public class TracingAutoConfiguration {
    private static final String INSTRUMENTATION_NAME = "tsb-sharedpackage";
    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

    @Bean
    @ConditionalOnMissingBean
    SdkTracerProvider sdkTracerProvider(TracingProperties properties, Environment environment) {
        SdkTracerProviderBuilderFactory builderFactory = new SdkTracerProviderBuilderFactory(properties, environment);
        return builderFactory.build();
    }

    @Bean
    @ConditionalOnMissingBean
    OpenTelemetry openTelemetry(SdkTracerProvider tracerProvider) {
        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    OtelCurrentTraceContext otelCurrentTraceContext() {
        return new OtelCurrentTraceContext();
    }

    @Bean
    @ConditionalOnMissingBean
    Tracer tracer(OpenTelemetry openTelemetry, OtelCurrentTraceContext traceContext) {
        return new OtelTracer(openTelemetry.getTracer(INSTRUMENTATION_NAME), traceContext, event -> {
        });
    }

    @Bean
    @ConditionalOnMissingBean
    TraceIdentityEnricher traceIdentityEnricher(Tracer tracer, TracingProperties properties) {
        return new TraceIdentityEnricher(tracer, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    Propagator propagator(OpenTelemetry openTelemetry) {
        return new OtelPropagator(openTelemetry.getPropagators(), openTelemetry.getTracer(INSTRUMENTATION_NAME));
    }

    @Bean
    ObservationPredicate tsbTracingPathObservationPredicate(TracingProperties properties) {
        return (name, context) -> shouldObserve(properties, context);
    }

    private static boolean shouldObserve(TracingProperties properties, Observation.Context context) {
        if (context instanceof ServerRequestObservationContext serverContext
                && serverContext.getCarrier() != null) {
            String path = serverContext.getCarrier().getRequestURI();
            return !matches(path, properties.getExcludePaths());
        }
        return true;
    }

    private static boolean matches(String path, java.util.List<String> patterns) {
        if (path == null || patterns == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            if (pattern.endsWith("/**")) {
                String prefix = pattern.substring(0, pattern.length() - 3);
                if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                    return true;
                }
                continue;
            }
            if (path.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static class SdkTracerProviderBuilderFactory {
        private final TracingProperties properties;
        private final Environment environment;

        SdkTracerProviderBuilderFactory(TracingProperties properties, Environment environment) {
            this.properties = properties;
            this.environment = environment;
        }

        SdkTracerProvider build() {
            io.opentelemetry.sdk.trace.SdkTracerProviderBuilder builder = SdkTracerProvider.builder()
                    .setSampler(Sampler.alwaysOn())
                    .setResource(Resource.getDefault().merge(Resource.create(Attributes.of(
                            SERVICE_NAME,
                            serviceName()))));
            if (properties.isExportEnabled()) {
                builder.addSpanProcessor(BatchSpanProcessor.builder(OtlpGrpcSpanExporter.builder()
                                .setEndpoint(properties.getOtlpEndpoint())
                                .setTimeout(properties.getExportTimeout())
                                .build())
                        .build());
            }
            return builder.build();
        }

        private String serviceName() {
            if (properties.getServiceName() != null && !properties.getServiceName().isBlank()) {
                return properties.getServiceName();
            }
            return environment.getProperty("spring.application.name", "tsb-service");
        }
    }
}
