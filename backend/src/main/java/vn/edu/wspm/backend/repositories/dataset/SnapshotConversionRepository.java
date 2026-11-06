package vn.edu.wspm.backend.repositories.dataset;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.services.dataset.conversion.*;
import vn.edu.wspm.backend.services.dataset.validation.StoredValidationReport;
import vn.edu.wspm.backend.services.dataset.validation.ValidationReport;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec.invalid;
import static vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactResult.*;

/** Short claim/publication transactions only. No filesystem I/O while a snapshot row is locked. */
@Repository
public class SnapshotConversionRepository {
    @PersistenceContext private EntityManager em;
    private final ConversionArtifactCodec codec;
    private final tools.jackson.databind.ObjectMapper mapper;
    private final Clock clock;
    public SnapshotConversionRepository(ConversionArtifactCodec codec, tools.jackson.databind.ObjectMapper mapper, Clock clock) {
        this.codec = codec; this.mapper = mapper; this.clock = clock;
    }
    public record Claim(String snapshotId, String attemptId, String sourceType, String rawReference,
                        ValidationReport report, String validationSha256) {
        @Override public String toString() { return "ConversionClaim[snapshotId=" + snapshotId + ", attemptId=" + attemptId + "]"; }
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public DatasetSnapshotEntity read(String id) { return find(id, false); }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public Claim claim(VerifiedConversionInput input, String attempt) {
        requireUuid(attempt);
        if (input.report() == null || input.binding() == null) throw invalid();
        var s = find(input.report().snapshotId(), true);
        var claim = new Claim(s.getSnapshotId(), attempt, input.binding().sourceType(), input.binding().rawReference(),
                input.report(), codec.validationHash(input.report()));
        requireBound(s, claim);
        if (!"NOT_STARTED".equals(s.getConversionReportJson().get("conversionStatus"))) throw invalid();
        requireUnpublished(s); requireNoRuns(s.getSnapshotId());
        var root = new LinkedHashMap<>(s.getConversionReportJson());
        root.put("conversionStatus", "RUNNING");
        root.put("conversion", Map.of("version", ConversionArtifactCodec.VERSION, "attemptId", attempt,
                "startedAt", now(), "validationSha256", claim.validationSha256(), "policies", ConversionArtifactCodec.POLICIES));
        s.setConversionReportJson(root); em.flush();
        return claim;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void publish(Claim claim, ConversionArtifactResult result) {
        var s = find(claim.snapshotId(), true);
        requireBound(s, claim); requireOwned(s, claim); requireUnpublished(s); requireNoRuns(s.getSnapshotId());
        if (!claim.snapshotId().equals(result.snapshotId()) || !claim.attemptId().equals(result.attemptId())
                || !claim.validationSha256().equals(result.validationSha256())) throw invalid();
        requireDescriptor(result.snapshotId(), result.attemptId(), "sequences.spmf", result.sequence());
        requireDescriptor(result.snapshotId(), result.attemptId(), "mapping.json", result.mapping());
        var stats = result.statistics(); requireStatistics(stats);
        s.setSequenceFilePath(result.sequence().reference()); s.setSequenceSha256(result.sequence().sha256());
        s.setMappingFilePath(result.mapping().reference()); s.setMappingSha256(result.mapping().sha256());
        s.setSequenceCount(stats.sequenceCount()); s.setSingleItemsetSequenceCount(stats.singleItemsetSequences());
        s.setMultiItemsetSequenceCount(stats.multiItemsetSequences()); s.setItemsetCount(stats.itemsetCount());
        s.setDistinctItemCount(stats.distinctItems()); s.setTotalItemOccurrences(stats.totalItemOccurrences());
        s.setMinSequenceLength(stats.minSequenceLength()); s.setMaxSequenceLength(stats.maxSequenceLength());
        s.setAverageSequenceLength(stats.averageSequenceLength());
        var root = new LinkedHashMap<>(s.getConversionReportJson());
        var conversion = new LinkedHashMap<String, Object>(operation(s));
        conversion.put("completedAt", now()); conversion.put("sequenceSizeBytes", Long.toString(result.sequence().sizeBytes()));
        conversion.put("mappingSizeBytes", Long.toString(result.mapping().sizeBytes()));
        root.put("conversion", conversion); root.put("conversionStatus", "SUCCEEDED"); root.put("statisticsStatus", "COMPUTED");
        s.setConversionReportJson(root); em.flush();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void fail(Claim claim) {
        var s = find(claim.snapshotId(), true);
        requireOwned(s, claim); requireUnpublished(s);
        var root = new LinkedHashMap<>(s.getConversionReportJson());
        var operation = new LinkedHashMap<String, Object>(operation(s));
        operation.put("failedAt", now()); operation.put("failureCode", "CONVERSION_FAILED");
        root.put("conversion", operation); root.put("conversionStatus", "FAILED");
        s.setConversionReportJson(root); em.flush();
    }

    /** Strict metadata decode for verified idempotent reads; does not prove the files exist. */
    public ConversionArtifactResult completed(DatasetSnapshotEntity s) {
        var root = s.getConversionReportJson();
        if (root == null || !"SUCCEEDED".equals(root.get("conversionStatus")) || !"COMPUTED".equals(root.get("statisticsStatus"))) throw invalid();
        var op = operation(s);
        String attempt = text(op, "attemptId"); requireUuid(attempt);
        String hash = text(op, "validationSha256");
        var report = StoredValidationReport.read(mapper, s);
        if (report == null || !"VALID".equals(report.validationStatus()) || !"READY".equals(report.cleaningOutcome())
                || !report.countsComplete() || !codec.validationHash(report).equals(hash)
                || !"wdm-order-csv-v1".equals(root.get("contractVersion"))
                || !Set.of("CSV", "DATABASE").contains(s.getSourceType())
                || !(s.getSnapshotId() + "/raw.csv").equals(s.getRawFilePath())) throw invalid();
        Instant.parse(text(op, "startedAt")); Instant.parse(text(op, "completedAt"));
        var sequence = new ArtifactFile(s.getSequenceFilePath(), s.getSequenceSha256(), size(op, "sequenceSizeBytes"));
        var mapping = new ArtifactFile(s.getMappingFilePath(), s.getMappingSha256(), size(op, "mappingSizeBytes"));
        requireDescriptor(s.getSnapshotId(), attempt, "sequences.spmf", sequence);
        requireDescriptor(s.getSnapshotId(), attempt, "mapping.json", mapping);
        var stats = new Statistics(number(s.getSequenceCount()), number(s.getSingleItemsetSequenceCount()), number(s.getMultiItemsetSequenceCount()),
                number(s.getItemsetCount()), number(s.getDistinctItemCount()), number(s.getTotalItemOccurrences()),
                integer(s.getMinSequenceLength()), integer(s.getMaxSequenceLength()), decimal(s.getAverageSequenceLength()));
        requireStatistics(stats);
        return new ConversionArtifactResult(s.getSnapshotId(), attempt, hash, sequence, mapping, stats);
    }

    private void requireBound(DatasetSnapshotEntity s, Claim claim) {
        var report = StoredValidationReport.read(mapper, s);
        if (!Objects.equals(report, claim.report()) || !"VALID".equals(s.getValidationStatus())
                || !"READY".equals(claim.report().cleaningOutcome()) || !claim.report().countsComplete()
                || !Objects.equals(s.getSourceType(), claim.sourceType()) || !Set.of("CSV", "DATABASE").contains(s.getSourceType())
                || !Objects.equals(s.getRawFilePath(), claim.rawReference()) || !(s.getSnapshotId() + "/raw.csv").equals(s.getRawFilePath())
                || !"wdm-order-csv-v1".equals(s.getConversionReportJson().get("contractVersion"))) throw invalid();
    }
    private void requireOwned(DatasetSnapshotEntity s, Claim claim) {
        var op = operation(s);
        if (!"RUNNING".equals(s.getConversionReportJson().get("conversionStatus"))
                || !claim.attemptId().equals(op.get("attemptId")) || !claim.validationSha256().equals(op.get("validationSha256"))) throw invalid();
    }
    private void requireUnpublished(DatasetSnapshotEntity s) {
        if (!"NOT_COMPUTED".equals(s.getConversionReportJson().get("statisticsStatus")) || !Long.valueOf(0).equals(s.getSequenceCount())
                || s.getSequenceFilePath() != null || s.getSequenceSha256() != null || s.getMappingFilePath() != null || s.getMappingSha256() != null
                || s.getSingleItemsetSequenceCount() != null || s.getMultiItemsetSequenceCount() != null || s.getItemsetCount() != null
                || s.getDistinctItemCount() != null || s.getTotalItemOccurrences() != null || s.getMinSequenceLength() != null
                || s.getMaxSequenceLength() != null || s.getAverageSequenceLength() != null) throw invalid();
    }
    private void requireNoRuns(String id) {
        if (((Number) em.createNativeQuery("SELECT COUNT(*) FROM MINING_RUN WHERE snapshot_id = :id").setParameter("id", id).getSingleResult()).longValue() != 0) throw invalid();
    }
    private DatasetSnapshotEntity find(String id, boolean lock) {
        var s = lock ? em.find(DatasetSnapshotEntity.class, id, LockModeType.PESSIMISTIC_WRITE, Map.of("jakarta.persistence.lock.timeout", 0))
                : em.find(DatasetSnapshotEntity.class, id);
        if (s == null) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        return s;
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> operation(DatasetSnapshotEntity s) {
        var root = s.getConversionReportJson();
        if (root == null || !(root.get("conversion") instanceof Map<?, ?> op) || !ConversionArtifactCodec.VERSION.equals(op.get("version"))
                || !ConversionArtifactCodec.POLICIES.equals(op.get("policies"))) throw invalid();
        return (Map<String, Object>) op;
    }
    private static void requireDescriptor(String id, String attempt, String name, ArtifactFile file) {
        if (file == null || !ConversionArtifactStorage.reference(id, attempt, name).equals(file.reference())
                || file.sha256() == null || !file.sha256().matches("[0-9a-f]{64}") || file.sizeBytes() <= 0) throw invalid();
    }
    private static void requireStatistics(Statistics s) {
        if (s.sequenceCount() <= 0 || s.singleItemsetSequences() < 0 || s.multiItemsetSequences() < 0
                || s.singleItemsetSequences() + s.multiItemsetSequences() != s.sequenceCount() || s.itemsetCount() < s.sequenceCount()
                || s.distinctItems() <= 0 || s.totalItemOccurrences() < s.itemsetCount() || s.minSequenceLength() <= 0
                || s.maxSequenceLength() < s.minSequenceLength() || !Double.isFinite(s.averageSequenceLength())
                || s.averageSequenceLength() != (double) s.totalItemOccurrences() / s.sequenceCount()) throw invalid();
    }
    private static String text(Map<String, Object> op, String key) { if (!(op.get(key) instanceof String value)) throw invalid(); return value; }
    private static long size(Map<String, Object> op, String key) { String value = text(op, key); if (!value.matches("[1-9][0-9]{0,18}")) throw invalid(); return Long.parseLong(value); }
    private static long number(Long value) { if (value == null || value < 0) throw invalid(); return value; }
    private static int integer(Integer value) { if (value == null || value <= 0) throw invalid(); return value; }
    private static double decimal(Double value) { if (value == null) throw invalid(); return value; }
    private static void requireUuid(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw invalid();
    }
    private String now() { return clock.instant().truncatedTo(ChronoUnit.MICROS).toString(); }
}
