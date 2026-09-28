package vn.com.truongsonbank.shared.crypto;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;

@Target(FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EncryptedField {
    boolean searchable() default false;

    String hashField() default "";
}
