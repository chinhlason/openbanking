package vn.com.truongsonbank.shared.exception;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.com.truongsonbank.shared.response.TraceIdProvider;

@AutoConfiguration
@ConditionalOnClass(RestControllerAdvice.class)
@ConditionalOnProperty(prefix = "tsb.shared.exception", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TsbExceptionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages", "errors", "tsb-shared-messages");
        source.setDefaultEncoding("UTF-8");
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    @Bean
    @ConditionalOnMissingBean
    ErrorMessageResolver errorMessageResolver(MessageSource messageSource) {
        return new ErrorMessageResolver(messageSource);
    }

    @Bean
    @ConditionalOnMissingBean
    LanguageResolver languageResolver() {
        return new LanguageResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    TsbExceptionHandler tsbExceptionHandler(
            ErrorMessageResolver messageResolver,
            LanguageResolver languageResolver,
            TraceIdProvider traceIdProvider) {
        return new TsbExceptionHandler(messageResolver, languageResolver, traceIdProvider);
    }
}
