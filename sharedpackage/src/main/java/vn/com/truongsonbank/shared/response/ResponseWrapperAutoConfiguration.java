package vn.com.truongsonbank.shared.response;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import io.micrometer.tracing.Tracer;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration
@ConditionalOnClass(ResponseBodyAdvice.class)
@ConditionalOnProperty(prefix = "tsb.shared.response", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ResponseWrapperAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TraceIdProvider traceIdProvider(ObjectProvider<Tracer> tracer) {
        return new TraceIdProvider(tracer);
    }

    @Bean
    @ConditionalOnMissingBean
    ResponseWrapperAdvice responseWrapperAdvice(ObjectMapper objectMapper, TraceIdProvider traceIdProvider) {
        return new ResponseWrapperAdvice(objectMapper, traceIdProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    FilterRegistrationBean<ResponseWrapperFilter> responseWrapperFilter(TraceIdProvider traceIdProvider) {
        FilterRegistrationBean<ResponseWrapperFilter> bean = new FilterRegistrationBean<>(new ResponseWrapperFilter(traceIdProvider));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return bean;
    }
}
