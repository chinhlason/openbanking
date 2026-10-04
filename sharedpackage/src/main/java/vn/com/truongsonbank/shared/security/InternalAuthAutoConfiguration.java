package vn.com.truongsonbank.shared.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import vn.com.truongsonbank.shared.config.TsbCommonConfigClient;
import vn.com.truongsonbank.shared.tracing.TraceIdentityEnricher;

@AutoConfiguration
@EnableConfigurationProperties(InternalAuthProperties.class)
@ConditionalOnProperty(prefix = "tsb.shared.internal-auth", name = "enabled", havingValue = "true")
public class InternalAuthAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    InternalAuthSecretProvider internalAuthSecretProvider(
            InternalAuthProperties properties,
            ObjectProvider<TsbCommonConfigClient> commonConfigClient,
            Environment environment) {
        return new InternalAuthSecretProvider(properties, commonConfigClient.getIfAvailable(), environment);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthHeaderSigner authHeaderSigner(InternalAuthSecretProvider secretProvider) {
        return new AuthHeaderSigner(secretProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    InternalAuthNonceStore internalAuthNonceStore(ObjectProvider<StringRedisTemplate> redis, InternalAuthProperties properties) {
        StringRedisTemplate redisTemplate = redis.getIfAvailable();
        if (redisTemplate != null) {
            return new RedisInternalAuthNonceStore(redisTemplate);
        }
        return new LocalInternalAuthNonceStore(properties.getMaxSkew());
    }

    @Bean
    @ConditionalOnProperty(prefix = "tsb.shared.internal-auth", name = "verifier-enabled", havingValue = "true")
    InternalAuthVerificationFilter internalAuthVerificationFilter(
            InternalAuthProperties properties,
            InternalAuthSecretProvider secretProvider,
            InternalAuthNonceStore nonceStore,
            vn.com.truongsonbank.shared.response.TraceIdProvider traceIdProvider,
            TraceIdentityEnricher traceIdentityEnricher) {
        return new InternalAuthVerificationFilter(properties, secretProvider, nonceStore, traceIdProvider,
                traceIdentityEnricher,
                new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
    }

    @Bean
    @ConditionalOnMissingBean
    InternalAuthorizationAspect internalAuthorizationAspect() {
        return new InternalAuthorizationAspect();
    }
}
