package vn.edu.wspm.backend.dto.run;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Configured-value constraints only. Does not resolve or approve effective thresholds. */
public final class RunMinimumSupportValidator
        implements ConstraintValidator<ValidRunMinimumSupport, RunCreateRequest> {
    @Override
    public boolean isValid(RunCreateRequest input, ConstraintValidatorContext context) {
        // Required fields and supported mode names have their own field constraints.
        if (input == null || input.minsupValue() == null) return true;
        double value = input.minsupValue();
        boolean valid = Double.isFinite(value) && value >= 0.0
                && (!"PERCENT".equals(input.minsupMode()) || value <= 100.0);
        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("minsupValue").addConstraintViolation();
        }
        return valid;
    }
}
