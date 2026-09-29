package vn.com.truongsonbank.shared.protocol;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.api.OpenTelemetry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;
import vn.com.truongsonbank.shared.security.AuthHeaderSigner;

@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@EnableConfigurationProperties(ProtocolProperties.class)
public class TsbProtocolAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    TsbProtocolInstrumentation tsbProtocolInstrumentation(MeterRegistry meterRegistry) {
        return new TsbProtocolInstrumentation(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    TsbProtocolPolicyResolver tsbProtocolPolicyResolver(ProtocolProperties properties) {
        return new TsbProtocolPolicyResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    TsbServiceDiscoveryClient tsbServiceDiscoveryClient(
            ProtocolProperties properties,
            ObjectProvider<ObjectMapper> objectMapper) {
        return new TsbServiceDiscoveryClient(properties, objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean
    TsbConsulServiceRegistrar tsbConsulServiceRegistrar(
            ProtocolProperties properties,
            Environment environment,
            ObjectProvider<ObjectMapper> objectMapper) {
        return new TsbConsulServiceRegistrar(properties, environment, objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean
    TsbCircuitBreakerRegistry tsbCircuitBreakerRegistry() {
        return new TsbCircuitBreakerRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    TsbProtocolClientHttpRequestInterceptor tsbProtocolClientHttpRequestInterceptor(
            TsbProtocolPolicyResolver policyResolver,
            TsbProtocolInstrumentation instrumentation,
            TsbCircuitBreakerRegistry circuitBreakers,
            ObjectProvider<Tracer> tracer,
            ObjectProvider<AuthHeaderSigner> authHeaderSigner) {
        return new TsbProtocolClientHttpRequestInterceptor(policyResolver, instrumentation, circuitBreakers, tracer, authHeaderSigner);
    }

    @Bean
    @ConditionalOnMissingBean
    public TsbHttpClientFactory tsbHttpClientFactory(
            ProtocolProperties properties,
            TsbProtocolClientHttpRequestInterceptor interceptor,
            TsbProtocolPolicyResolver policyResolver,
            TsbServiceDiscoveryClient discoveryClient) {
        return new TsbHttpClientFactory(properties, interceptor, policyResolver, discoveryClient);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(ManagedChannelBuilder.class)
    TsbGrpcClientInterceptor tsbGrpcClientInterceptor(
            TsbProtocolPolicyResolver policyResolver,
            ObjectProvider<Tracer> tracer) {
        return new TsbGrpcClientInterceptor(policyResolver, tracer);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(ManagedChannelBuilder.class)
    public TsbGrpcClientFactory tsbGrpcClientFactory(
            ProtocolProperties properties,
            TsbGrpcClientInterceptor interceptor,
            TsbServiceDiscoveryClient discoveryClient) {
        return new TsbGrpcClientFactory(properties, interceptor, discoveryClient);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(ManagedChannelBuilder.class)
    public TsbGrpcServerInterceptor tsbGrpcServerInterceptor(OpenTelemetry openTelemetry) {
        return new TsbGrpcServerInterceptor(openTelemetry);
    }
}
