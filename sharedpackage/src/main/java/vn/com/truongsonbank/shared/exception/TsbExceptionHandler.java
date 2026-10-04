package vn.com.truongsonbank.shared.exception;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import vn.com.truongsonbank.shared.response.ResponseWrapperFilter;
import vn.com.truongsonbank.shared.response.TsbResponse;
import vn.com.truongsonbank.shared.response.TraceIdProvider;

@RestControllerAdvice
class TsbExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(TsbExceptionHandler.class);

    private final ErrorMessageResolver messageResolver;
    private final LanguageResolver languageResolver;
    private final TraceIdProvider traceIdProvider;

    TsbExceptionHandler(ErrorMessageResolver messageResolver, LanguageResolver languageResolver, TraceIdProvider traceIdProvider) {
        this.messageResolver = messageResolver;
        this.languageResolver = languageResolver;
        this.traceIdProvider = traceIdProvider;
    }

    @ExceptionHandler(TsbException.class)
    ResponseEntity<TsbResponse<Object>> handleTsbException(TsbException exception, HttpServletRequest request) {
        ErrorDescriptor error = exception.error();
        Locale locale = languageResolver.resolve(request);
        String message = messageResolver.resolve(error, exception.args(), locale);
        log.error("Business Error occurred: code={}, message={}, traceId={}",
                error.code(), message, traceIdProvider.resolve(request), exception);
        return response(error.httpStatus(), error.code(), message, request);
    }

    @ExceptionHandler({ResponseStatusException.class, ErrorResponseException.class})
    ResponseEntity<TsbResponse<Object>> handleSpringStatus(RuntimeException exception, HttpServletRequest request) {
        int status = status(exception);
        String code = code(status);
        String message = exception.getMessage();
        log.error("Response Error occurred: code={}, message={}, traceId={}",
                code, message, traceIdProvider.resolve(request), exception);
        return response(status, code, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<TsbResponse<Object>> handleUnreadableBody(HttpMessageNotReadableException exception, HttpServletRequest request) {
        ErrorDescriptor error = CommonErrors.BAD_REQUEST;
        log.warn("Bad request body: code={}, message={}, traceId={}",
                error.code(), exception.getMessage(), traceIdProvider.resolve(request), exception);
        return response(error.httpStatus(), error.code(), error.defaultMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<TsbResponse<Object>> handleNoResource(NoResourceFoundException exception, HttpServletRequest request) {
        ErrorDescriptor error = CommonErrors.NOT_FOUND;
        log.warn("Resource not found: code={}, message={}, traceId={}",
                error.code(), exception.getMessage(), traceIdProvider.resolve(request));
        return response(error.httpStatus(), error.code(), error.defaultMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<TsbResponse<Object>> handleException(Exception exception, HttpServletRequest request) {
        ErrorDescriptor error = CommonErrors.INTERNAL_ERROR;
        Locale locale = languageResolver.resolve(request);
        String message = messageResolver.resolve(error, null, locale);
        log.error("Unexpected Error occurred: code={}, message={}, traceId={}",
                error.code(), message, traceIdProvider.resolve(request), exception);
        return response(error.httpStatus(), error.code(), message, request);
    }

    private ResponseEntity<TsbResponse<Object>> response(int status, String code, String message, HttpServletRequest request) {
        TsbResponse<Object> body = new TsbResponse<>(
                traceIdProvider.resolve(request),
                false,
                code,
                message,
                duration(request),
                Instant.now(),
                null);
        return ResponseEntity.status(status).body(body);
    }

    private int status(RuntimeException exception) {
        if (exception instanceof ResponseStatusException statusException) {
            return statusException.getStatusCode().value();
        }
        if (exception instanceof ErrorResponseException errorResponseException) {
            return errorResponseException.getStatusCode().value();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private String code(int status) {
        return switch (status) {
            case 400 -> CommonErrors.BAD_REQUEST.code();
            case 401 -> CommonErrors.UNAUTHORIZED.code();
            case 403 -> CommonErrors.FORBIDDEN.code();
            case 404 -> CommonErrors.NOT_FOUND.code();
            case 409 -> CommonErrors.CONFLICT.code();
            case 429 -> CommonErrors.RATE_LIMIT.code();
            default -> CommonErrors.INTERNAL_ERROR.code();
        };
    }

    private String duration(HttpServletRequest request) {
        Object start = request == null ? null : request.getAttribute(ResponseWrapperFilter.START_NANOS);
        if (!(start instanceof Long startNanos)) {
            return null;
        }
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos) + "ms";
    }
}
