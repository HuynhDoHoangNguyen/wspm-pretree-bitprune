package vn.edu.wspm.backend.services.dataset.conversion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.dataset.SnapshotConversionRepository;
import vn.edu.wspm.backend.services.dataset.RawSnapshotStorage;
import vn.edu.wspm.backend.services.dataset.validation.StoredValidationReport;
import vn.edu.wspm.backend.services.dataset.validation.ValidationLimits;

import java.util.UUID;
import java.util.concurrent.Semaphore;

/** Internal synchronous conversion orchestration. The public conversion controller is intentionally not wired here yet. */
@Service
@Slf4j
public class SnapshotConversionService {
    private final ConversionValidationGate gate;
    private final ProductItemMappingService mapping;
    private final CustomerGroupingService grouping;
    private final TemporalOrderingService ordering;
    private final SpmfDatasetSerializer serializer;
    private final ConversionArtifactCodec codec;
    private final ConversionArtifactStorage storage;
    private final SnapshotConversionRepository repository;
    private final RawSnapshotStorage raw;
    private final ValidationLimits limits;
    private final Semaphore capacity;
    private final ConversionReadService reads;

    public SnapshotConversionService(ConversionValidationGate gate, ProductItemMappingService mapping,
            CustomerGroupingService grouping, TemporalOrderingService ordering, SpmfDatasetSerializer serializer,
            ConversionArtifactCodec codec, ConversionArtifactStorage storage, SnapshotConversionRepository repository,
            RawSnapshotStorage raw, ValidationLimits limits, ConversionReadService reads) {
        this.gate = gate; this.mapping = mapping; this.grouping = grouping; this.ordering = ordering;
        this.serializer = serializer; this.codec = codec; this.storage = storage; this.repository = repository;
        this.raw = raw; this.limits = limits; this.capacity = new Semaphore(limits.maxConcurrent());
        this.reads = reads;
    }

    public ConversionArtifactResult convert(String id) {
        StoredValidationReport.canonicalId(id);
        if (!capacity.tryAcquire()) throw unavailable();
        SnapshotConversionRepository.Claim claim = null;
        ConversionArtifactStorage.Receipt receipt = null;
        boolean publicationStarted = false;
        long start = System.nanoTime();
        try {
            var snapshot = repository.read(id);
            if (snapshot.getConversionReportJson() != null && "SUCCEEDED".equals(snapshot.getConversionReportJson().get("conversionStatus"))) {
                var completed = reads.verified(id).result(); budget(start); return completed;
            }
            var proof = gate.prepare(id); budget(start);
            claim = repository.claim(proof, UUID.randomUUID().toString());
            log.info("Conversion claimed: snapshotId={}, attemptId={}", id, claim.attemptId());
            var dictionary = mapping.resolve(proof); budget(start);
            var ordered = ordering.order(grouping.group(proof, dictionary)); budget(start);
            byte[] sequence = serializer.serialize(ordered), dictionaryBytes = codec.mappingBytes(dictionary); budget(start);
            receipt = storage.save(id, claim.attemptId(), sequence, dictionaryBytes);
            var actual = codec.verify(storage.read(id, claim.attemptId(), "sequences.spmf", receipt.sequence()),
                    storage.read(id, claim.attemptId(), "mapping.json", receipt.mapping()));
            codec.requireReadback(ordered, actual); budget(start);
            raw.verifiedBytes(id, claim.rawReference(), claim.report().rawSha256(), claim.report().rawSizeBytes());
            budget(start);
            var result = new ConversionArtifactResult(id, claim.attemptId(), claim.validationSha256(),
                    receipt.sequence(), receipt.mapping(), actual.statistics());
            publicationStarted = true; // any exception from publication/commit keeps files, even if rollback is reported
            repository.publish(claim, result);
            var confirmed = reads.verified(id).result();
            budget(start);
            log.info("Conversion published: snapshotId={}, attemptId={}, sequences={}", id, claim.attemptId(), confirmed.statistics().sequenceCount());
            return confirmed;
        } catch (Exception failure) {
            if (claim != null && !publicationStarted) {
                try {
                    repository.fail(claim); // prove unpublished before deleting receipt-owned files
                    if (receipt != null) storage.discard(receipt);
                } catch (RuntimeException uncertain) {
                    log.warn("Conversion failure outcome requires reconciliation: snapshotId={}, attemptId={}, failureType={}",
                            id, claim.attemptId(), uncertain.getClass().getSimpleName());
                }
            }
            if (publicationStarted) log.warn("Conversion publication requires reconciliation; retain artifacts: snapshotId={}", id);
            if (failure instanceof ApplicationException expected) {
                log.warn("Conversion rejected: snapshotId={}, code={}", id, expected.code());
                throw expected;
            }
            var safe = new IllegalStateException("Conversion failed: " + failure.getClass().getSimpleName());
            safe.setStackTrace(failure.getStackTrace());
            log.error("Conversion unavailable: snapshotId={}", id, safe);
            throw unavailable();
        } finally { capacity.release(); }
    }

    private void budget(long start) {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() - start > limits.maxDurationMillis() * 1_000_000L) throw unavailable();
    }
    private static ApplicationException unavailable() {
        return new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE, "Conversion could not be confirmed; automatic retry is not supported.");
    }
}
