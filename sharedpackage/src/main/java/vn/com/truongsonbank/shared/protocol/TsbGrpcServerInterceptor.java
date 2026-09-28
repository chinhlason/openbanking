package vn.com.truongsonbank.shared.protocol;

import java.net.SocketAddress;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class TsbGrpcServerInterceptor implements ServerInterceptor {
    private static final String INSTRUMENTATION_NAME = "tsb-sharedpackage";
    private static final Logger log = LoggerFactory.getLogger(TsbGrpcServerInterceptor.class);
    private final OpenTelemetry openTelemetry;

    public TsbGrpcServerInterceptor(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        String method = call.getMethodDescriptor().getFullMethodName();
        long startNanos = System.nanoTime();
        Context parent = openTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), headers, MetadataGetter.INSTANCE);
        Span span = openTelemetry.getTracer(INSTRUMENTATION_NAME)
                .spanBuilder("grpc server " + method)
                .setParent(parent)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("rpc.system", "grpc")
                .setAttribute("rpc.service", service(method))
                .setAttribute("rpc.method", rpcMethod(method))
                .startSpan();
        ServerCall<ReqT, RespT> loggingCall = new LoggingServerCall<>(call, method, startNanos, span);
        Scope scope = span.makeCurrent();
        try {
            return new Listener<>(next.startCall(loggingCall, headers), span);
        } finally {
            scope.close();
        }
    }

    private static String service(String method) {
        int index = method.indexOf('/');
        return index < 0 ? method : method.substring(0, index);
    }

    private static String rpcMethod(String method) {
        int index = method.indexOf('/');
        return index < 0 ? method : method.substring(index + 1);
    }

    private static class Listener<ReqT> extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {
        private final Span span;

        Listener(ServerCall.Listener<ReqT> delegate, Span span) {
            super(delegate);
            this.span = span;
        }

        @Override
        public void onComplete() {
            try {
                super.onComplete();
                span.setStatus(StatusCode.OK);
            } finally {
                span.end();
            }
        }

        @Override
        public void onCancel() {
            try {
                super.onCancel();
                span.setStatus(StatusCode.ERROR);
            } finally {
                span.end();
            }
        }
    }

    private static class LoggingServerCall<ReqT, RespT> extends ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> {
        private final String method;
        private final long startNanos;
        private final Span span;

        LoggingServerCall(ServerCall<ReqT, RespT> delegate, String method, long startNanos, Span span) {
            super(delegate);
            this.method = method;
            this.startNanos = startNanos;
            this.span = span;
        }

        @Override
        public void close(Status status, Metadata trailers) {
            String previousTraceId = MDC.get("traceId");
            String previousTrace_id = MDC.get("trace_id");
            String traceId = span.getSpanContext().getTraceId();
            try {
                MDC.put("traceId", traceId);
                MDC.put("trace_id", traceId);
                log.info(logJson(status));
            } finally {
                restore("traceId", previousTraceId);
                restore("trace_id", previousTrace_id);
            }
            super.close(status, trailers);
        }

        private String logJson(Status status) {
            return "{"
                    + "\"event\":\"grpc_request\","
                    + "\"timestamp\":\"" + Instant.now() + "\","
                    + "\"traceId\":\"" + span.getSpanContext().getTraceId() + "\","
                    + "\"method\":\"" + escape(rpcMethod(method)) + "\","
                    + "\"path\":\"" + escape(method) + "\","
                    + "\"status\":\"" + status.getCode().name() + "\","
                    + "\"durationMs\":" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos) + ","
                    + "\"clientIp\":\"" + escape(clientIp()) + "\""
                    + "}";
        }

        private String clientIp() {
            SocketAddress remote = getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
            return remote == null ? "" : remote.toString();
        }

        private static void restore(String key, String value) {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        }

        private static String escape(String value) {
            return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    private enum MetadataGetter implements TextMapGetter<Metadata> {
        INSTANCE;

        @Override
        public Iterable<String> keys(Metadata carrier) {
            return carrier.keys();
        }

        @Override
        public String get(Metadata carrier, String key) {
            if (carrier == null) {
                return null;
            }
            return carrier.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
        }
    }
}
