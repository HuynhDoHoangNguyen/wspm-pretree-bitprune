package vn.edu.wspm.backend.services.run;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.wspm.backend.common.api.FieldErrorDetail;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.config.ResolvedMinimumSupport;
import java.util.List;
import java.util.Objects;

/** Application operational scope only; does not define zero-threshold mining semantics. */
@Component
@Slf4j
public class RunConfigurationValidityGate {
    public ResolvedMinimumSupport requireSupported(ResolvedMinimumSupport resolved) {
        Objects.requireNonNull(resolved, "resolved minimum support is required");
        double threshold = resolved.effectiveThreshold();
        if (resolved.numberOfSequences() <= 0 || !Double.isFinite(threshold) || threshold < 0.0) {
            throw new IllegalArgumentException("resolved minimum support violates the verified-input contract");
        }
        if (threshold == 0.0) {
            log.debug("Mining configuration rejected: zero effective threshold, R6 remains open");
            throw new ApplicationException(ErrorCode.UNSUPPORTED_MINING_CONFIGURATION,
                    ErrorCode.UNSUPPORTED_MINING_CONFIGURATION.message(), List.of(new FieldErrorDetail(
                    "minsupValue", "ZERO_EFFECTIVE_THRESHOLD",
                    "Effective minimum support must be positive while R6 remains open.")));
        }
        return resolved;
    }
}
