package vn.edu.wspm.backend.services.run;

import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.common.api.FieldErrorDetail;
import vn.edu.wspm.backend.dto.run.RunCreateRequest;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.dataset.SnapshotConversionRepository;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionReadService;
import vn.edu.wspm.backend.services.dataset.validation.ValidationLimits;
import vn.edu.wspm.backend.services.run.engine.MiningAlgorithm;
import vn.edu.wspm.backend.services.run.engine.MiningEngineRequest;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportMode;
import vn.edu.wspm.statistics.ItemStatisticsCalculator;
import vn.edu.wspm.statistics.ItemWeightCalculator;

/** Bounded, read-only preparation. No lock is held while verifying/parsing files or computing weights. */
@Service
@Slf4j
public class RunPreparationService {
    private final Validator validator;
    private final ConversionReadService reads;
    private final ConversionArtifactCodec codec;
    private final SnapshotConversionRepository snapshots;
    private final RunThresholdResolutionService thresholds;
    private final RunConfigurationValidityGate gate;
    private final ValidationLimits limits;
    private final Semaphore capacity;

    public RunPreparationService(Validator validator, ConversionReadService reads, ConversionArtifactCodec codec,
            SnapshotConversionRepository snapshots, RunThresholdResolutionService thresholds,
            RunConfigurationValidityGate gate, ValidationLimits limits) {
        this.validator = validator; this.reads = reads; this.codec = codec; this.snapshots = snapshots;
        this.thresholds = thresholds; this.gate = gate; this.limits = limits;
        this.capacity = new Semaphore(limits.maxConcurrent());
    }

    public PreparedRun prepare(RunCreateRequest request) {
        return prepare(request, null).input();
    }
    public MeasuredRunPreparation prepareMeasured(RunCreateRequest request, java.util.function.LongSupplier ticker) {
        return prepare(request, java.util.Objects.requireNonNull(ticker));
    }
    private MeasuredRunPreparation prepare(RunCreateRequest request, java.util.function.LongSupplier ticker) {
        if (request == null) throw ApplicationException.invalid("request", "Supply a run request.");
        var errors = validator.validate(request).stream().map(v -> new FieldErrorDetail(
                v.getPropertyPath().toString(), "INVALID_VALUE", "Supply a valid value."))
                .distinct().sorted(Comparator.comparing(FieldErrorDetail::field)).toList();
        if (!errors.isEmpty()) throw new ApplicationException(ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.message(), errors);
        if (!capacity.tryAcquire()) throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
        long start = System.nanoTime();
        try {
            var measured = ticker == null ? null : java.util.Objects.requireNonNull(reads.verifiedMeasured(request.snapshotId(), ticker));
            var bundle = measured == null ? reads.verified(request.snapshotId()) : measured.bundle(); budget(start);
            var publication = bundle.result();
            var verified = codec.verify(bundle.sequenceBytes(), bundle.mappingBytes()); budget(start);
            if (!request.snapshotId().equals(publication.snapshotId())
                    || !publication.statistics().equals(verified.statistics())) throw ConversionArtifactCodec.invalid();
            var snapshot = snapshots.read(request.snapshotId());
            if (!publication.equals(snapshots.completed(snapshot))) throw ConversionArtifactCodec.invalid();
            var resolved = gate.requireSupported(thresholds.resolve(request, verified.database().size()));
            var statistics = new ItemStatisticsCalculator().calculate(verified.database());
            var weights = new ItemWeightCalculator().calculate(statistics); budget(start);
            var engine = new MiningEngineRequest(MiningAlgorithm.valueOf(request.algorithm()), verified.database(),
                    new MinimumSupport(request.minsupValue(), MinimumSupportMode.valueOf(request.minsupMode())));
            var binding = new PreparedRun.Binding(publication, snapshot.getSourceType(), snapshot.getSourceName(),
                    snapshot.getRawFilePath(), snapshot.getRawSha256());
            // completed() checks the validation hash/binding; creation rechecks these values under row lock.
            log.debug("Run input prepared: snapshotId={}, algorithm={}, mode={}, sequenceCount={}, configured={}, effective={}",
                    request.snapshotId(), engine.algorithm(), resolved.mode(), resolved.numberOfSequences(),
                    resolved.configuredValue(), resolved.effectiveThreshold());
            return new MeasuredRunPreparation(new PreparedRun(engine, resolved, weights, binding), measured == null ? 0 : measured.datasetIoNanos());
        } catch (ApplicationException expected) { throw expected; }
        catch (RuntimeException failure) {
            var safe = new IllegalStateException("Run preparation failed: " + failure.getClass().getSimpleName());
            safe.setStackTrace(failure.getStackTrace());
            log.error("Run preparation unavailable: snapshotId={}", request.snapshotId(), safe);
            throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
        } finally { capacity.release(); }
    }

    private void budget(long start) {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() - start > limits.maxDurationMillis() * 1_000_000L) {
            throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
