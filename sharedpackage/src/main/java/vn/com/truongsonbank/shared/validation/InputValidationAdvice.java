package vn.com.truongsonbank.shared.validation;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import vn.com.truongsonbank.shared.exception.CommonErrors;
import vn.com.truongsonbank.shared.exception.ValidationException;

@ControllerAdvice
class InputValidationAdvice extends RequestBodyAdviceAdapter {

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object afterBodyRead(
            Object body,
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        validate(body);
        return body;
    }

    private void validate(Object body) {
        if (body == null) {
            return;
        }
        for (Field field : body.getClass().getDeclaredFields()) {
            InputValidator validator = field.getAnnotation(InputValidator.class);
            if (validator == null) {
                continue;
            }
            validateField(body, field, validator);
        }
    }

    private void validateField(Object body, Field field, InputValidator validator) {
        Object value = value(body, field);
        String text = value == null ? null : value.toString();

        if (validator.required() && !hasText(text)) {
            throw error(CommonErrors.VALIDATION_REQUIRED, validator.fieldName());
        }
        if (!hasText(text)) {
            return;
        }
        if (validator.min() >= 0 && text.length() < validator.min()) {
            throw error(CommonErrors.VALIDATION_MIN, validator.fieldName(), validator.min());
        }
        if (validator.max() >= 0 && text.length() > validator.max()) {
            throw error(CommonErrors.VALIDATION_MAX, validator.fieldName(), validator.max());
        }
        if (hasText(validator.regex()) && !text.matches(validator.regex())) {
            throw error(CommonErrors.VALIDATION_REGEX, validator.fieldName());
        }
    }

    private Object value(Object body, Field field) {
        try {
            field.setAccessible(true);
            return field.get(body);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot validate field " + field.getName(), e);
        }
    }

    private ValidationException error(CommonErrors error, Object... args) {
        return new ValidationException(error, args);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
