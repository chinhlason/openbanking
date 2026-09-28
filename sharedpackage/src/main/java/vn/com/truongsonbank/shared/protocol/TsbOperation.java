package vn.com.truongsonbank.shared.protocol;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TsbOperation {
    String value() default "";

    String responseTimeout() default "";

    int retryAttempts() default -1;

    String retryInitialDelay() default "";

    String retryMaxDelay() default "";

    boolean retryJitter() default true;

    boolean circuitBreakerEnabled() default true;

    int circuitBreakerMinimumCalls() default -1;

    int circuitBreakerSlidingWindowSize() default -1;

    float circuitBreakerFailureRateThreshold() default -1;

    String circuitBreakerOpenDuration() default "";

    int circuitBreakerHalfOpenCalls() default -1;
}
