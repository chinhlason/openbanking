package vn.com.truongsonbank.shared.protocol;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.com.truongsonbank.shared.security.ServiceTokenManager;

class TsbGrpcClientInterceptor {
    private static final Metadata.Key<String> TRACEPARENT =
            Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> IDEMPOTENCY_KEY =
            Metadata.Key.of("idempotency-key", Metadata.ASCII_STRING_MARSHALLER);

    private final TsbProtocolPolicyResolver policyResolver;
    private final ObjectProvider<Tracer> tracer;
    private final ObjectProvider<ServiceTokenManager> serviceTokenManager;

    TsbGrpcClientInterceptor(TsbProtocolPolicyResolver policyResolver, ObjectProvider<Tracer> tracer,
                             ObjectProvider<ServiceTokenManager> serviceTokenManager) {
        this.policyResolver = policyResolver;
        this.tracer = tracer;
        this.serviceTokenManager = serviceTokenManager;
    }

    ClientInterceptor forDownstream(String downstream) {
        return new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method,
                    CallOptions callOptions,
                    Channel next) {
                return intercept(downstream, method, callOptions, next);
            }
        };
    }

    private <ReqT, RespT> ClientCall<ReqT, RespT> intercept(
            String downstream,
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {
        String operation = method.getFullMethodName();
        Duration timeout = policyResolver.resolve(downstream, operation).responseTimeout();
        ClientCall<ReqT, RespT> delegate = next.newCall(method, callOptions.withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS));
        return new ForwardingClientCall.SimpleForwardingClientCall<>(delegate) {
            private Span span;

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                span = startSpan(downstream, operation);
                propagate(headers, downstream, operation);
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(io.grpc.Status status, Metadata trailers) {
                        tag(span, "grpc.status", status.getCode().name());
                        tag(span, "outcome", status.isOk() ? "success" : "error");
                        end(span);
                        super.onClose(status, trailers);
                    }
                }, headers);
            }
        };
    }

    private void propagate(Metadata headers, String downstream, String operation) {
        Tracer currentTracer = tracer.getIfAvailable();
        Span span = currentTracer == null ? null : currentTracer.currentSpan();
        if (span != null && !headers.containsKey(TRACEPARENT)) {
            headers.put(TRACEPARENT, "00-" + span.context().traceId() + "-" + span.context().spanId() + "-01");
        }
        String idempotencyKey = inboundHeader("Idempotency-Key");
        if (idempotencyKey != null && !headers.containsKey(IDEMPOTENCY_KEY)) {
            headers.put(IDEMPOTENCY_KEY, idempotencyKey);
        }
        ProtocolProperties.ServiceAuth auth = policyResolver.resolve(downstream, operation).serviceAuth();
        if (auth.isEnabled()) {
            ServiceTokenManager manager = serviceTokenManager.getIfAvailable();
            if (manager == null) throw new vn.com.truongsonbank.shared.exception.TsbException(vn.com.truongsonbank.shared.security.ServiceAuthErrors.TOKEN_FETCH_FAILED);
            headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                    "Bearer " + manager.getToken(auth));
        }
    }

    private Span startSpan(String downstream, String operation) {
        Tracer currentTracer = tracer.getIfAvailable();
        if (currentTracer == null) {
            return null;
        }
        Span span = currentTracer.nextSpan().name("grpc client " + operation).start();
        tag(span, "rpc.system", "grpc");
        tag(span, "rpc.service", operation.contains("/") ? operation.substring(0, operation.indexOf('/')) : operation);
        tag(span, "rpc.method", operation.contains("/") ? operation.substring(operation.indexOf('/') + 1) : operation);
        tag(span, "peer.service", downstream);
        return span;
    }

    private void tag(Span span, String key, String value) {
        if (span != null) {
            span.tag(key, value);
        }
    }

    private void end(Span span) {
        if (span != null) {
            span.end();
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
}
