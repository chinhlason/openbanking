package vn.com.truongsonbank.shared.response;

import java.lang.reflect.AnnotatedElement;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RestControllerAdvice
class ResponseWrapperAdvice implements ResponseBodyAdvice<Object> {
    private final ObjectMapper objectMapper;
    private final TraceIdProvider traceIdProvider;

    ResponseWrapperAdvice(ObjectMapper objectMapper, TraceIdProvider traceIdProvider) {
        this.objectMapper = objectMapper;
        this.traceIdProvider = traceIdProvider;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return annotated(returnType.getContainingClass()) || annotated(returnType.getMethod());
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body instanceof TsbResponse<?>) {
            return body;
        }

        TsbResponse<Object> wrapped = TsbResponse.success(traceId(), duration(), body);
        if (StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType)) {
            try {
                return objectMapper.writeValueAsString(wrapped);
            } catch (JacksonException e) {
                throw new IllegalStateException("Cannot serialize wrapped response", e);
            }
        }
        return wrapped;
    }

    private boolean annotated(AnnotatedElement element) {
        return element != null && element.isAnnotationPresent(ResponseWrapper.class);
    }

    private String traceId() {
        return traceIdProvider.resolve(currentRequest());
    }

    private String duration() {
        HttpServletRequest request = currentRequest();
        Object start = request == null ? null : request.getAttribute(ResponseWrapperFilter.START_NANOS);
        if (!(start instanceof Long startNanos)) {
            return null;
        }
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        return millis + "ms";
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
