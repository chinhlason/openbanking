package vn.com.truongsonbank.shared.kafka;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TsbKafkaIdempotent {
    String key() default "";

    long ttlSeconds() default 86_400;

    long inProgressTtlSeconds() default 300;
}
