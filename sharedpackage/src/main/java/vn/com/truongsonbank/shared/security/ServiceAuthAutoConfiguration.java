package vn.com.truongsonbank.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@AutoConfiguration
@ConditionalOnClass(JwtDecoder.class)
@EnableConfigurationProperties(ServiceAuthProperties.class)
public class ServiceAuthAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "tsb.shared.security.service-auth", name = "enabled", havingValue = "true")
    JwtDecoder serviceJwtDecoder(ServiceAuthProperties properties) {
        if (properties.getJwkSetUri() == null || properties.getJwkSetUri().isBlank()) {
            throw new IllegalStateException("tsb.shared.security.service-auth.jwk-set-uri is required");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
        decoder.setJwtValidator(new ServiceJwtValidator(properties));
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "tsb.shared.security.service-auth", name = "enabled", havingValue = "true")
    ServiceJwtVerifier serviceJwtVerifier(JwtDecoder decoder, ServiceAuthProperties properties) {
        return new ServiceJwtVerifier(decoder, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "tsb.shared.security.service-auth", name = "enabled", havingValue = "true")
    ServiceAuthGrpcServerInterceptor serviceAuthGrpcServerInterceptor(ServiceJwtVerifier verifier) {
        return new ServiceAuthGrpcServerInterceptor(verifier);
    }

    @Bean
    @ConditionalOnMissingBean
    ServiceTokenManager serviceTokenManager(ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistry) {
        return new ServiceTokenManager(RestClient.builder(), meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "tsb.shared.security.service-auth", name = "enabled", havingValue = "true")
    ServiceAuthVerificationFilter serviceAuthVerificationFilter(
            ServiceJwtVerifier verifier, ServiceAuthProperties properties, ObjectProvider<ObjectMapper> objectMapper) {
        return new ServiceAuthVerificationFilter(verifier, properties,
                objectMapper.getIfAvailable(() -> new ObjectMapper().findAndRegisterModules()));
    }

    @Bean
    @ConditionalOnProperty(prefix = "tsb.shared.security.service-auth.permission-resolver", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    ServicePermissionResolver servicePermissionResolver(
            ServiceAuthProperties properties, ServiceTokenManager tokenManager) {
        ServicePermissionResolver resolver = new RemoteServicePermissionResolver(RestClient.builder(), properties, tokenManager);
        ServiceAuthBeans.RESOLVER.set(resolver);
        return resolver;
    }

    @Bean
    @ConditionalOnBean({RedisConnectionFactory.class, ServicePermissionResolver.class})
    @ConditionalOnProperty(prefix = "tsb.shared.security.service-auth.permission-resolver", name = "enabled", havingValue = "true")
    RedisMessageListenerContainer servicePermissionRedisContainer(
                RedisConnectionFactory connectionFactory,
                ServicePermissionResolver resolver,
                ServiceAuthProperties properties,
                ObjectProvider<ObjectMapper> objectMapper) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                new ServicePermissionInvalidationListener(
                        objectMapper.getIfAvailable(() -> new ObjectMapper().findAndRegisterModules()), resolver),
                new ChannelTopic(properties.getPermissionResolver().getRedisChannel()));
        return container;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "tsb.shared.security.service-auth", name = "enabled", havingValue = "true")
    InternalAuthorizationAspect serviceAuthorizationAspect() {
        return new InternalAuthorizationAspect();
    }

    private static final class ServiceJwtValidator implements OAuth2TokenValidator<Jwt> {
        private final OAuth2TokenValidator<Jwt> delegate;
        private final String expectedAudience;

        private ServiceJwtValidator(ServiceAuthProperties properties) {
            this.delegate = properties.getIssuerUri() == null || properties.getIssuerUri().isBlank()
                    ? JwtValidators.createDefault()
                    : JwtValidators.createDefaultWithIssuer(properties.getIssuerUri());
            this.expectedAudience = properties.getExpectedAudience();
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            OAuth2TokenValidatorResult base = delegate.validate(token);
            if (base.hasErrors()) return base;
            if (expectedAudience != null && !expectedAudience.isBlank() && !token.getAudience().contains(expectedAudience)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid service token audience", null));
            }
            return OAuth2TokenValidatorResult.success();
        }
    }
}
