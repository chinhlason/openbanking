package vn.com.truongsonbank.shared.protocol;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class TsbProtocolClientHttpRequestInterceptor {
    static final String OPERATION_HEADER = "X-TSB-Operation";
    private static final Logger log = LoggerFactory.getLogger(TsbProtocolClientHttpRequestInterceptor.class);
    private static final ExecutorService TIMEOUT_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private final TsbProtocolPolicyResolver policyResolver;
    private final TsbProtocolInstrumentation instrumentation;
    private final TsbCircuitBreakerRegistry circuitBreakers;
    private final ObjectProvider<Tracer> tracer;

    TsbProtocolClientHttpRequestInterceptor(
            TsbProtocolPolicyResolver policyResolver,
            TsbProtocolInstrumentation instrumentation,
            TsbCircuitBreakerRegistry circuitBreakers,
            ObjectProvider<Tracer> tracer) {
        this.policyResolver = policyResolver;
        this.instrumentation = instrumentation;
        this.circuitBreakers = circuitBreakers;
        this.tracer = tracer;
    }

    ClientHttpRequestInterceptor forDownstream(String downstream) {
        return (request, body, execution) -> intercept(downstream, request, body, execution);
    }

    private ClientHttpResponse intercept(
            String downstream,
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        String operation = operation(request);
        if (operation == null) {
            operation = policyResolver.operationForPath(downstream, request.getURI().getPath());
        }
        if (operation == null) {
            operation = request.getMethod().name() + " " + request.getURI().getPath();
        }
        propagateContext(request);
        ResolvedProtocolPolicy policy = policyResolver.resolve(downstream, operation);
        CircuitBreaker circuitBreaker = null;
        if (policy.circuitBreaker().isEnabled()) {
            circuitBreaker = circuitBreakers.get(policy);
            if (!circuitBreaker.tryAcquirePermission()) {
                instrumentation.count("circuitbreaker.rejected", downstream, operation, "open");
                throw CallNotPermittedException.createCallNotPermittedException(circuitBreaker);
            }
        }

        int maxAttempts = 1 + Math.max(policy.retry().getAttempts(), 0);
        boolean retryAllowed = retryAllowed(request, policy);
        IOException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long startNanos = System.nanoTime();
            try {
                ClientHttpResponse response = executeWithTimeout(policy.responseTimeout(), request, body, execution);
                int status = response.getStatusCode().value();
                Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
                boolean retryableStatus = retryableStatus(response.getStatusCode());
                if (retryableStatus && retryAllowed && attempt < maxAttempts) {
                    record(circuitBreaker, duration, new IOException("Retryable HTTP status " + status));
                    instrumentation.count("retry", downstream, operation, "status_" + status);
                    sleepBeforeRetry(policy, response, attempt);
                    response.close();
                    continue;
                }
                record(circuitBreaker, duration, retryableStatus ? new IOException("Retryable HTTP status " + status) : null);
                instrumentation.recordDuration(downstream, operation, request.getMethod().name(), outcome(status), String.valueOf(status), duration);
                logIfNeeded(policy, downstream, operation, request, status, duration, retryableStatus);
                return response;
            } catch (IOException e) {
                lastError = e;
                Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
                record(circuitBreaker, duration, e);
                instrumentation.recordDuration(downstream, operation, request.getMethod().name(), "error", "IO", duration);
                if (!retryAllowed || attempt >= maxAttempts || !retryableException(e)) {
                    instrumentation.count("error", downstream, operation, "io");
                    logIfNeeded(policy, downstream, operation, request, "IO", duration, true);
                    throw e;
                }
                instrumentation.count("retry", downstream, operation, "io");
                sleep(backoff(policy, attempt));
            } catch (RuntimeException e) {
                Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
                record(circuitBreaker, duration, e);
                instrumentation.recordDuration(downstream, operation, request.getMethod().name(), "error", e.getClass().getSimpleName(), duration);
                instrumentation.count("error", downstream, operation, "runtime");
                logIfNeeded(policy, downstream, operation, request, e.getClass().getSimpleName(), duration, true);
                throw e;
            }
        }
        throw lastError == null ? new IOException("HTTP retry exhausted") : lastError;
    }

    private ClientHttpResponse executeWithTimeout(
            Duration timeout,
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return execution.execute(request, body);
        }
        Future<ClientHttpResponse> future = TIMEOUT_EXECUTOR.submit(() -> execution.execute(request, body));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new SocketTimeoutException("HTTP operation timed out after " + timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during HTTP call", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IOException("HTTP call failed", cause);
        }
    }

    private String operation(HttpRequest request) {
        String operation = request.getHeaders().getFirst(OPERATION_HEADER);
        if (operation != null && !operation.isBlank()) {
            return operation;
        }
        return null;
    }

    private void propagateContext(HttpRequest request) {
        Tracer currentTracer = tracer.getIfAvailable();
        Span span = currentTracer == null ? null : currentTracer.currentSpan();
        if (span != null && !request.getHeaders().containsHeader("traceparent")) {
            request.getHeaders().set("traceparent", "00-" + span.context().traceId() + "-" + span.context().spanId() + "-01");
        }
        String idempotencyKey = inboundHeader("Idempotency-Key");
        if (idempotencyKey != null && !request.getHeaders().containsHeader("Idempotency-Key")) {
            request.getHeaders().set("Idempotency-Key", idempotencyKey);
        }
    }

    private String inboundHeader(String name) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            String value = servletRequestAttributes.getRequest().getHeader(name);
            return value == null || value.isBlank() ? null : value;
        }
        return null;
    }

    private boolean retryAllowed(HttpRequest request, ResolvedProtocolPolicy policy) {
        if (policy.retry().getAttempts() <= 0) {
            return false;
        }
        HttpMethod method = request.getMethod();
        if (method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.PUT
                || method == HttpMethod.DELETE || method == HttpMethod.OPTIONS) {
            return true;
        }
        return !policy.retry().isRequireIdempotencyForUnsafeMethods()
                || request.getHeaders().containsHeader("Idempotency-Key");
    }

    private boolean retryableStatus(HttpStatusCode status) {
        int value = status.value();
        return value == 429 || value == 502 || value == 503 || value == 504;
    }

    private boolean retryableException(IOException e) {
        return e instanceof SocketTimeoutException || e.getClass().getName().contains("Connect");
    }

    private String outcome(int status) {
        return status >= 200 && status < 500 ? "success" : "error";
    }

    private void record(CircuitBreaker circuitBreaker, Duration duration, Throwable error) {
        if (circuitBreaker == null) {
            return;
        }
        if (error == null) {
            circuitBreaker.onSuccess(duration.toNanos(), TimeUnit.NANOSECONDS);
        } else {
            circuitBreaker.onError(duration.toNanos(), TimeUnit.NANOSECONDS, error);
        }
    }

    private void sleepBeforeRetry(ResolvedProtocolPolicy policy, ClientHttpResponse response, int attempt) throws IOException {
        Duration retryAfter = retryAfter(response.getHeaders(), policy.retry().getMaxRetryAfter());
        sleep(retryAfter == null ? backoff(policy, attempt) : retryAfter);
    }

    private Duration retryAfter(HttpHeaders headers, Duration max) {
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }
        Duration delay;
        try {
            delay = Duration.ofSeconds(Long.parseLong(value));
        } catch (NumberFormatException e) {
            try {
                delay = Duration.between(Instant.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        if (delay.isNegative()) {
            return Duration.ZERO;
        }
        return delay.compareTo(max) > 0 ? max : delay;
    }

    private Duration backoff(ResolvedProtocolPolicy policy, int attempt) {
        ProtocolProperties.Backoff backoff = policy.retry().getBackoff();
        long base = Math.max(backoff.getInitialDelay().toMillis(), 0);
        long max = Math.max(backoff.getMaxDelay().toMillis(), base);
        long delay = base;
        if ("exponential".equalsIgnoreCase(backoff.getStrategy())) {
            delay = Math.min(max, base * (1L << Math.min(attempt - 1, 10)));
        }
        if (backoff.isJitter() && delay > 1) {
            delay = ThreadLocalRandom.current().nextLong(delay / 2, delay + 1);
        }
        return Duration.ofMillis(delay);
    }

    private void sleep(Duration duration) throws IOException {
        try {
            Thread.sleep(Math.max(duration.toMillis(), 0));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during HTTP retry backoff", e);
        }
    }

    private void logIfNeeded(
            ResolvedProtocolPolicy policy,
            String downstream,
            String operation,
            HttpRequest request,
            Object status,
            Duration duration,
            boolean failure) {
        ProtocolProperties.OutboundLog logConfig = policy.log();
        if (!logConfig.isEnabled() && !(failure && logConfig.isLogFailures())) {
            return;
        }
        log.info("event=http_outbound downstream={} operation={} method={} uri={} status={} durationMs={}",
                downstream,
                operation,
                request.getMethod().name().toUpperCase(Locale.ROOT),
                request.getURI(),
                status,
                duration.toMillis());
    }
}
