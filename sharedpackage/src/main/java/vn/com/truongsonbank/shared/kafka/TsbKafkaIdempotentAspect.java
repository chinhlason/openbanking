package vn.com.truongsonbank.shared.kafka;

import java.time.Duration;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

@Aspect
class TsbKafkaIdempotentAspect {
    private static final String PREFIX = "tsb:kafka:idempotent:";
    private final StringRedisTemplate redis;
    private final KafkaInstrumentation instrumentation;
    private final ExpressionParser parser = new SpelExpressionParser();

    TsbKafkaIdempotentAspect(StringRedisTemplate redis, KafkaInstrumentation instrumentation) {
        this.redis = redis;
        this.instrumentation = instrumentation;
    }

    @Around("@annotation(annotation)")
    Object around(ProceedingJoinPoint joinPoint, TsbKafkaIdempotent annotation) throws Throwable {
        ConsumerRecord<?, ?> record = record(joinPoint.getArgs());
        String topic = record == null ? "unknown" : record.topic();
        String key = PREFIX + key(annotation, joinPoint, record);
        Boolean locked = redis.opsForValue().setIfAbsent(key, "PROCESSING", Duration.ofSeconds(annotation.inProgressTtlSeconds()));
        if (!Boolean.TRUE.equals(locked)) {
            instrumentation.count("idempotent.skip", topic, "duplicate");
            return null;
        }
        try {
            Object result = joinPoint.proceed();
            redis.opsForValue().set(key, "DONE", Duration.ofSeconds(annotation.ttlSeconds()));
            instrumentation.count("idempotent.complete", topic, "success");
            return result;
        } catch (Throwable error) {
            redis.delete(key);
            instrumentation.count("idempotent.complete", topic, "error", error.getClass().getSimpleName());
            throw error;
        }
    }

    private String key(TsbKafkaIdempotent annotation, ProceedingJoinPoint joinPoint, ConsumerRecord<?, ?> record) {
        if (annotation.key().isBlank()) {
            if (record == null) {
                throw new IllegalArgumentException("@TsbKafkaIdempotent requires key when ConsumerRecord argument is missing");
            }
            return record.topic() + ":" + record.partition() + ":" + record.offset();
        }
        StandardEvaluationContext context = new StandardEvaluationContext();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);
        }
        if (record != null) {
            context.setVariable("record", record);
        }
        return String.valueOf(parser.parseExpression(annotation.key()).getValue(context));
    }

    private ConsumerRecord<?, ?> record(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof ConsumerRecord<?, ?> consumerRecord) {
                return consumerRecord;
            }
        }
        return null;
    }
}
