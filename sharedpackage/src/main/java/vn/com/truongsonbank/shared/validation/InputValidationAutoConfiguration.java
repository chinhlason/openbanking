package vn.com.truongsonbank.shared.validation;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

@AutoConfiguration
@ConditionalOnClass(RequestBodyAdvice.class)
@ConditionalOnProperty(prefix = "tsb.shared.validation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InputValidationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    InputValidationAdvice inputValidationAdvice() {
        return new InputValidationAdvice();
    }
}
