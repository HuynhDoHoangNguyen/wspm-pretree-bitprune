package vn.edu.wspm.backend.services.run;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.common.api.FieldErrorDetail;
import vn.edu.wspm.backend.dto.run.MiningPreviewResponse;
import vn.edu.wspm.backend.dto.run.RunCreateRequest;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.mappers.MiningPreviewResponseMapper;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionReadService;

import java.util.Comparator;
import java.util.List;

/** Read-only preview. Shared verifier owns resource state, integrity, budgets and safe failures. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MiningPreviewService {
    private final Validator validator;
    private final ConversionReadService reads;
    private final RunThresholdResolutionService thresholds;
    private final MiningPreviewResponseMapper mapper;

    public MiningPreviewResponse preview(RunCreateRequest request) {
        if (request == null) throw ApplicationException.invalid("request", "Supply a run request.");
        List<FieldErrorDetail> details = validator.validate(request).stream()
                .map(v -> new FieldErrorDetail(v.getPropertyPath().toString(), "INVALID_VALUE", "Supply a valid value."))
                .distinct().sorted(Comparator.comparing(FieldErrorDetail::field)).toList();
        if (!details.isEmpty()) {
            log.debug("Mining preview rejected: invalid request fields");
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.message(), details);
        }
        var publication = reads.verified(request.snapshotId()).result();
        if (!request.snapshotId().equals(publication.snapshotId()) || publication.statistics() == null
                || publication.statistics().sequenceCount() <= 0 || publication.statistics().sequenceCount() > Integer.MAX_VALUE) {
            log.debug("Mining preview rejected: inconsistent verified metadata, snapshotId={}", request.snapshotId());
            throw ConversionArtifactCodec.invalid();
        }
        var resolved = thresholds.resolve(request, publication.statistics().sequenceCount());
        log.debug("Mining preview completed: snapshotId={}, algorithm={}, mode={}",
                request.snapshotId(), request.algorithm(), request.minsupMode());
        return mapper.map(publication, request.algorithm(), resolved);
    }
}
