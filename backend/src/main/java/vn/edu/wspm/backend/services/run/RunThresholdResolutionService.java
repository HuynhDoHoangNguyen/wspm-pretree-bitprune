package vn.edu.wspm.backend.services.run;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.common.api.FieldErrorDetail;
import vn.edu.wspm.backend.dto.run.RunCreateRequest;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportMode;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.config.ResolvedMinimumSupport;

import java.util.Comparator;
import java.util.List;

/** Internal configured-input adapter. Resolution does not establish execution eligibility. */
@Service
@Slf4j
@RequiredArgsConstructor
public class RunThresholdResolutionService {
    private final Validator validator;
    private final MinimumSupportResolver resolver = new MinimumSupportResolver();

    /**
     * The sequence count comes from a trusted dataset boundary, never the request JSON.
     * Invalid counts and percentage resolution on an empty database throw
     * IllegalArgumentException; future preview/gate callers own their HTTP translation.
     */
    public ResolvedMinimumSupport resolve(RunCreateRequest request, long numberOfSequences) {
        if (request == null) {
            log.debug("Threshold resolution rejected: missing request");
            throw ApplicationException.invalid("request", "Supply a run request.");
        }
        List<FieldErrorDetail> details = validator.validate(request).stream()
                .map(violation -> new FieldErrorDetail(violation.getPropertyPath().toString(),
                        "INVALID_VALUE", "Supply a valid value."))
                .distinct().sorted(Comparator.comparing(FieldErrorDetail::field)).toList();
        if (!details.isEmpty()) {
            log.debug("Threshold resolution rejected: invalid request fields");
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED,
                    ErrorCode.VALIDATION_FAILED.message(), details);
        }
        if (numberOfSequences < 0 || numberOfSequences > Integer.MAX_VALUE) {
            log.debug("Threshold resolution rejected: sequence count outside core range");
            throw new IllegalArgumentException("number of sequences must be between 0 and Integer.MAX_VALUE");
        }
        MinimumSupport configuration = new MinimumSupport(
                request.minsupValue(), MinimumSupportMode.valueOf(request.minsupMode()));
        log.debug("Resolving configured threshold: snapshotId={}, mode={}, sequenceCount={}",
                request.snapshotId(), configuration.mode(), numberOfSequences);
        return resolver.resolve(configuration, Math.toIntExact(numberOfSequences));
    }
}
