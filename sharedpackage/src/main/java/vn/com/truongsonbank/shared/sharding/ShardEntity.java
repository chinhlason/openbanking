package vn.com.truongsonbank.shared.sharding;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

@Target(TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ShardEntity {
    int db() default 1;

    int tb() default 1;

    ShardStrategy dbStrategy() default ShardStrategy.HASH;

    ShardStrategy tbStrategy() default ShardStrategy.HASH;

    ShardTimeUnit timeUnit() default ShardTimeUnit.MONTH;

    String tablePattern() default "";
}
