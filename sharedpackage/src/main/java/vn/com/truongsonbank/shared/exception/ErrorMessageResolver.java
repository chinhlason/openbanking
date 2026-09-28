package vn.com.truongsonbank.shared.exception;

import java.util.Locale;

import org.springframework.context.MessageSource;

class ErrorMessageResolver {
    private final MessageSource messageSource;

    ErrorMessageResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    String resolve(ErrorDescriptor error, Object[] args, Locale locale) {
        return messageSource.getMessage(error.code(), args, error.defaultMessage(), locale);
    }
}
