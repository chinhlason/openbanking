package vn.com.truongsonbank.shared.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.listener.RecordInterceptor;

class TsbKafkaRecordInterceptor implements RecordInterceptor<String, Object> {
    private static final String INSTRUMENTATION_NAME = "tsb-sharedpackage";
    private static final ThreadLocal<State> STATE = new ThreadLocal<>();
    private final OpenTelemetry openTelemetry;
    private final KafkaInstrumentation instrumentation;

    TsbKafkaRecordInterceptor(OpenTelemetry openTelemetry, KafkaInstrumentation instrumentation) {
        this.openTelemetry = openTelemetry;
        this.instrumentation = instrumentation;
    }

    @Override
    public ConsumerRecord<String, Object> intercept(ConsumerRecord<String, Object> record, Consumer<String, Object> consumer) {
        Context parent = openTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), record, HeaderGetter.INSTANCE);
        Span span = openTelemetry.getTracer(INSTRUMENTATION_NAME)
                .spanBuilder("kafka consume " + record.topic())
                .setParent(parent)
                .setSpanKind(SpanKind.CONSUMER)
                .setAttribute("messaging.system", "kafka")
                .setAttribute("messaging.destination.name", record.topic())
                .setAttribute("messaging.kafka.partition", record.partition())
                .setAttribute("messaging.kafka.offset", record.offset())
                .startSpan();
        Scope scope = span.makeCurrent();
        STATE.set(new State(span, scope, System.nanoTime()));
        return record;
    }

    @Override
    public void success(ConsumerRecord<String, Object> record, Consumer<String, Object> consumer) {
        finish(record, "success", null);
    }

    @Override
    public void failure(ConsumerRecord<String, Object> record, Exception exception, Consumer<String, Object> consumer) {
        finish(record, "error", exception);
    }

    @Override
    public void afterRecord(ConsumerRecord<String, Object> record, Consumer<String, Object> consumer) {
        finish(record, "unknown", null);
    }

    private void finish(ConsumerRecord<String, Object> record, String outcome, Exception error) {
        State state = STATE.get();
        if (state == null) {
            return;
        }
        STATE.remove();
        try {
            if (error != null) {
                state.span.recordException(error);
                state.span.setStatus(StatusCode.ERROR, error.getMessage());
            }
            state.span.setAttribute("outcome", outcome);
            instrumentation.recordConsume(record.topic(), outcome, Duration.ofNanos(System.nanoTime() - state.startNanos));
        } finally {
            state.scope.close();
            state.span.end();
        }
    }

    private record State(Span span, Scope scope, long startNanos) {
    }

    private enum HeaderGetter implements TextMapGetter<ConsumerRecord<String, Object>> {
        INSTANCE;

        @Override
        public Iterable<String> keys(ConsumerRecord<String, Object> record) {
            return record.headers().headers("traceparent") == null ? java.util.List.of() : java.util.List.of("traceparent");
        }

        @Override
        public String get(ConsumerRecord<String, Object> record, String key) {
            Header header = record.headers().lastHeader(key);
            return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
        }
    }
}
