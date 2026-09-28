package vn.com.truongsonbank.shared.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InputValidator {
    String fieldName();

    int min() default -1;

    int max() default -1;

    boolean required() default false;

    String regex() default "";
}
