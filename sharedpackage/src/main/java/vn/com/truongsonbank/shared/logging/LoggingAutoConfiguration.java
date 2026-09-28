package vn.com.truongsonbank.shared.logging;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import vn.com.truongsonbank.shared.response.TraceIdProvider;

@AutoConfiguration
@ConditionalOnClass(OncePerRequestFilter.class)
@EnableConfigurationProperties(LoggingProperties.class)
@ConditionalOnProperty(prefix = "tsb.shared.logging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SensitiveDataMasker sensitiveDataMasker(LoggingProperties properties) {
        return new SensitiveDataMasker(properties.getMaskRules());
    }

    @Bean
    @ConditionalOnMissingBean
    FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter(
            LoggingProperties properties,
            SensitiveDataMasker masker,
            TraceIdProvider traceIdProvider,
            ObjectMapper objectMapper) {
        FilterRegistrationBean<RequestLoggingFilter> bean = new FilterRegistrationBean<>(
                new RequestLoggingFilter(properties, masker, traceIdProvider, objectMapper));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        return bean;
    }
}
