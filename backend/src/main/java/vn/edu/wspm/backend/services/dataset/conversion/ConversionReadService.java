package vn.edu.wspm.backend.services.dataset.conversion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.dto.dataset.ConversionResponse;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.mappers.ConversionResponseMapper;
import vn.edu.wspm.backend.repositories.dataset.SnapshotConversionRepository;
import vn.edu.wspm.backend.services.dataset.RawSnapshotStorage;
import vn.edu.wspm.backend.services.dataset.validation.StoredValidationReport;
import vn.edu.wspm.backend.services.dataset.validation.ValidationLimits;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import static vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec.invalid;

/** Read-only status/integrity boundary shared by POST confirmation, GET and downloads. Never calls conversion. */
@Service
@Slf4j
public class ConversionReadService {
    private final SnapshotConversionRepository repository;
    private final ConversionArtifactCodec codec;
    private final ConversionArtifactStorage storage;
    private final RawSnapshotStorage raw;
    private final ValidationLimits limits;
    private final ConversionResponseMapper mapper;
    private final tools.jackson.databind.ObjectMapper json;
    private final Semaphore capacity;

    public ConversionReadService(SnapshotConversionRepository repository, ConversionArtifactCodec codec,
            ConversionArtifactStorage storage, RawSnapshotStorage raw, ValidationLimits limits, ConversionResponseMapper mapper,
            tools.jackson.databind.ObjectMapper json) {
        this.repository = repository; this.codec = codec; this.storage = storage; this.raw = raw;
        this.limits = limits; this.mapper = mapper; this.json = json; capacity = new Semaphore(limits.maxConcurrent());
    }

    public ConversionResponse status(String id) {
        StoredValidationReport.canonicalId(id);
        try { return statusOf(id, repository.read(id)); }
        catch (ApplicationException expected) { throw expected; }
        catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException failure) {
            var safe = new IllegalStateException("Conversion status read failed: " + failure.getClass().getSimpleName());
            safe.setStackTrace(failure.getStackTrace()); log.error("Conversion status unavailable: snapshotId={}", id, safe);
            throw unavailable();
        } catch (RuntimeException malformedMetadata) { throw invalid(); }
    }

    private ConversionResponse statusOf(String id, DatasetSnapshotEntity s) {
        var root = s.getConversionReportJson();
        if (root == null || !"wdm-order-csv-v1".equals(root.get("contractVersion"))) throw invalid();
        Object state = root.get("conversionStatus");
        if ("SUCCEEDED".equals(state)) return verified(id).response();
        requireUnpublished(s);
        if ("NOT_STARTED".equals(state)) {
            if (root.containsKey("conversion")) throw invalid();
            return mapper.pending(id, "NOT_STARTED", null, null, null);
        }
        if (!"RUNNING".equals(state) && !"FAILED".equals(state)) throw invalid();
        var op = operation(s);
        var report = StoredValidationReport.read(json, s);
        if (report == null || !"VALID".equals(report.validationStatus()) || !"READY".equals(report.cleaningOutcome())
                || !report.countsComplete() || !Set.of("CSV", "DATABASE").contains(s.getSourceType())
                || !(id + "/raw.csv").equals(s.getRawFilePath())) throw invalid();
        if (!(op.get("attemptId") instanceof String attempt) || !attempt.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || !(op.get("validationSha256") instanceof String hash) || !hash.matches("[0-9a-f]{64}")
                || !hash.equals(codec.validationHash(report))) {
            throw invalid();
        }
        Instant started = time(op, "startedAt");
        if (op.containsKey("completedAt") || op.containsKey("sequenceSizeBytes") || op.containsKey("mappingSizeBytes")) throw invalid();
        if ("RUNNING".equals(state)) {
            if (op.containsKey("failedAt") || op.containsKey("failureCode")) throw invalid();
            return mapper.pending(id, "RUNNING", started, null, null);
        }
        if (!"CONVERSION_FAILED".equals(op.get("failureCode"))) throw invalid();
        return mapper.pending(id, "FAILED", started, time(op, "failedAt"), "CONVERSION_FAILED");
    }

    public VerifiedBundle verified(String id) {
        StoredValidationReport.canonicalId(id);
        if (!capacity.tryAcquire()) throw unavailable();
        long start = System.nanoTime();
        try {
            var snapshot = repository.read(id);
            var result = repository.completed(snapshot);
            Instant started = time(operation(snapshot), "startedAt"), completed = time(operation(snapshot), "completedAt");
            raw.verifiedBytes(id, snapshot.getRawFilePath(), snapshot.getRawSha256(), StoredValidationReport.rawSize(snapshot)); budget(start);
            byte[] sequence = storage.read(id, result.attemptId(), "sequences.spmf", result.sequence());
            byte[] mapping = storage.read(id, result.attemptId(), "mapping.json", result.mapping()); budget(start);
            var verified = codec.verify(sequence, mapping); budget(start);
            var current = repository.read(id);
            if (!verified.statistics().equals(result.statistics()) || !result.equals(repository.completed(current))
                    || !started.equals(time(operation(current), "startedAt")) || !completed.equals(time(operation(current), "completedAt"))) throw invalid();
            log.debug("Conversion read verified: snapshotId={}, sequenceBytes={}, mappingBytes={}", id, sequence.length, mapping.length);
            return new VerifiedBundle(result, mapper.completed(result, started, completed), sequence, mapping,
                    snapshot.getRawSha256(), StoredValidationReport.rawSize(snapshot));
        } catch (ApplicationException expected) { throw expected; }
        catch (Exception failure) {
            var safe = new IllegalStateException("Conversion read failed: " + failure.getClass().getSimpleName());
            safe.setStackTrace(failure.getStackTrace()); log.error("Conversion read unavailable: snapshotId={}", id, safe);
            throw unavailable();
        } finally { capacity.release(); }
    }

    private void budget(long start) {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() - start > limits.maxDurationMillis() * 1_000_000L) throw unavailable();
    }
    private static void requireUnpublished(DatasetSnapshotEntity s) {
        if (!"NOT_COMPUTED".equals(s.getConversionReportJson().get("statisticsStatus")) || !Long.valueOf(0).equals(s.getSequenceCount())
                || s.getSequenceFilePath() != null || s.getSequenceSha256() != null || s.getMappingFilePath() != null || s.getMappingSha256() != null
                || s.getItemsetCount() != null || s.getSingleItemsetSequenceCount() != null || s.getMultiItemsetSequenceCount() != null
                || s.getDistinctItemCount() != null || s.getTotalItemOccurrences() != null || s.getMinSequenceLength() != null
                || s.getMaxSequenceLength() != null || s.getAverageSequenceLength() != null) throw invalid();
    }
    private static Map<?, ?> operation(DatasetSnapshotEntity s) {
        var root = s.getConversionReportJson();
        if (root == null || !(root.get("conversion") instanceof Map<?, ?> op) || !ConversionArtifactCodec.VERSION.equals(op.get("version"))
                || !ConversionArtifactCodec.POLICIES.equals(op.get("policies"))) throw invalid();
        return op;
    }
    private static Instant time(Map<?, ?> op, String key) {
        if (!(op.get(key) instanceof String value) || !value.endsWith("Z")) throw invalid();
        try { return Instant.parse(value); } catch (RuntimeException invalidTime) { throw invalid(); }
    }
    private static ApplicationException unavailable() { return new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE, "Conversion artifacts could not be verified or read."); }
    public static final class VerifiedBundle {
        private final ConversionArtifactResult result;
        private final ConversionResponse response;
        private final byte[] sequence, mapping;
        private final String rawSha256, rawSizeBytes;
        private VerifiedBundle(ConversionArtifactResult result, ConversionResponse response, byte[] sequence, byte[] mapping,
                               String rawSha256, String rawSizeBytes) {
            this.result = result; this.response = response; this.sequence = sequence.clone(); this.mapping = mapping.clone();
            this.rawSha256 = rawSha256; this.rawSizeBytes = rawSizeBytes;
        }
        public ConversionArtifactResult result() { return result; }
        public ConversionResponse response() { return response; }
        public byte[] sequenceBytes() { return sequence.clone(); }
        public byte[] mappingBytes() { return mapping.clone(); }
        /** Consumer-specific cap before defensive copying; verified reader has its own IO budgets. */
        public byte[] sequenceBytes(int maximum) { if(maximum<1 || sequence.length>maximum)throw invalid(); return sequence.clone(); }
        public byte[] mappingBytes(int maximum) { if(maximum<1 || mapping.length>maximum)throw invalid(); return mapping.clone(); }
        public String rawSha256() { return rawSha256; }
        public String rawSizeBytes() { return rawSizeBytes; }
        @Override public String toString() { return "VerifiedConversionBundle[snapshotId=" + result.snapshotId() + "]"; }
    }
}
