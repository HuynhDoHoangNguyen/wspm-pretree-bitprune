package vn.edu.wspm.backend.dto.run;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RunMinimumSupportValidator.class)
public @interface ValidRunMinimumSupport {
    String message() default "Supply a finite minimum support within the mode's range.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
