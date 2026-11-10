package vn.edu.wspm.backend.mappers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import vn.edu.wspm.backend.dto.miningrun.MiningRunDetailResponse;
import vn.edu.wspm.backend.dto.miningrun.MiningRunSummaryResponse;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.entities.RunEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningRunResponseMapperTest {

    private final MiningRunResponseMapper mapper = new MiningRunResponseMapper();

    @Test
    void shouldNotExposeAcquisitionPlaceholderCountsAsComputedStatistics() {
        DatasetSnapshotEntity snapshot = testSnapshot("snap-pending", 0L, 0L, "PENDING");
        snapshot.setConversionReportJson(Map.of("statisticsStatus", "NOT_COMPUTED"));
        MiningRunDetailResponse detail = mapper.toDetailResponse(populatedRun(), snapshot);
        assertNull(detail.sequenceCount());
        assertNull(detail.distinctItemCount());
        assertEquals("PENDING", detail.validationStatus());
        assertEquals(0L, snapshot.getSequenceCount());
    }

    @Test
    void shouldPreserveExplicitlyComputedZeroStatistics() {
        DatasetSnapshotEntity snapshot = testSnapshot("snap-empty", 0L, 0L, "VALID");
        snapshot.setConversionReportJson(Map.of("statisticsStatus", "COMPUTED"));
        MiningRunDetailResponse detail = mapper.toDetailResponse(populatedRun(), snapshot);
        assertEquals(0L, detail.sequenceCount());
        assertEquals(0L, detail.distinctItemCount());
    }

    @Test
    void shouldMapEveryDetailFieldAndConvertLifecycleTimesAsUtc() {
        RunEntity run = populatedRun();
        DatasetSnapshotEntity snapshot = testSnapshot("snap-distinct", 500L, 42L, "VALID");

        MiningRunDetailResponse expected = new MiningRunDetailResponse(
                "run-001", "WSPM_BITPRUNE", "SUCCEEDED", "BENCHMARK", "snap-distinct",
                "PERCENTAGE", 5.0, 25.0, 17L, 91L, 101L, 123456L,
                Instant.parse("2026-11-03T23:59:59.123456Z"),
                Instant.parse("2026-11-04T00:00:00.234567Z"),
                Instant.parse("2026-11-04T00:00:01.345678Z"),
                0.05, 0.8, 0.1, 0.3, 11L, 12L, 13L, 25L, 14L,
                null, 500L, 42L, "VALID");

        assertEquals(expected, mapper.toDetailResponse(run, snapshot));
    }

    @Test
    void shouldMapEverySummaryField() {
        MiningRunSummaryResponse expected = new MiningRunSummaryResponse(
                "run-001", "WSPM_BITPRUNE", "SUCCEEDED", "BENCHMARK", "snap-distinct",
                "PERCENTAGE", 5.0, 25.0, 17L, 91L, 101L, 123456L,
                Instant.parse("2026-11-03T23:59:59.123456Z"),
                Instant.parse("2026-11-04T00:00:00.234567Z"),
                Instant.parse("2026-11-04T00:00:01.345678Z"));

        assertEquals(expected, mapper.toSummaryResponse(populatedRun()));
    }

    @Test
    void shouldKeepRunSnapshotIdWhenSnapshotMetadataIsUnavailable() {
        MiningRunDetailResponse detail = mapper.toDetailResponse(populatedRun(), null);
        assertEquals("snap-distinct", detail.snapshotId());
        assertNull(detail.sequenceCount());
        assertNull(detail.distinctItemCount());
        assertNull(detail.validationStatus());
    }

    @Test
    void shouldPreserveNullConfigurationMeasurementsAndTimestamps() {
        RunEntity run = new RunEntity("run-null", "WSPM_PRETREE", "BENCHMARK",
                null, null, null, null, null, null);
        MiningRunDetailResponse detail = mapper.toDetailResponse(run, null);
        Arrays.asList(detail.minsupConfigured(), detail.minsupRelativeFraction(),
                detail.effectiveThreshold(), detail.maxW(), detail.minW(), detail.meanW(),
                detail.patternCount(), detail.candidateCount(), detail.datasetIoMs(),
                detail.preprocessingMs(), detail.dfsMiningMs(), detail.completeAlgorithmMs(),
                detail.outputIoMs(), detail.totalExecutionMs(), detail.peakSampledJvmHeapBytes(),
                detail.createdAt(), detail.startedAt(), detail.finishedAt(), detail.errorMessage())
                .forEach(value -> assertNull(value));
        MiningRunSummaryResponse summary = mapper.toSummaryResponse(run);
        Arrays.asList(summary.minsupConfigured(), summary.effectiveThreshold(),
                summary.patternCount(), summary.candidateCount(), summary.totalExecutionMs(),
                summary.peakSampledJvmHeapBytes(), summary.createdAt(), summary.startedAt(), summary.finishedAt())
                .forEach(value -> assertNull(value));
    }

    @Test
    void shouldPreserveExplicitZeroValuesInEveryNumericScope() {
        RunEntity run = populatedRun();
        for (String field : List.of("patternCount", "candidateCount", "datasetIoMs", "preprocessingMs",
                "dfsMiningMs", "completeAlgorithmMs", "outputIoMs", "totalExecutionMs", "peakSampledJvmHeapBytes")) {
            ReflectionTestUtils.setField(run, field, 0L);
        }
        for (String field : List.of("minsupConfigured", "minsupRatio", "minsupAbs", "maxW", "minW", "meanW")) {
            ReflectionTestUtils.setField(run, field, 0.0);
        }
        MiningRunDetailResponse detail = mapper.toDetailResponse(run,
                testSnapshot("snap-distinct", 0L, 0L, "VALID"));
        List.of(detail.patternCount(), detail.candidateCount(), detail.datasetIoMs(), detail.preprocessingMs(),
                detail.dfsMiningMs(), detail.completeAlgorithmMs(), detail.outputIoMs(), detail.totalExecutionMs(),
                detail.peakSampledJvmHeapBytes(), detail.sequenceCount(), detail.distinctItemCount())
                .forEach(value -> assertEquals(0L, value));
        List.of(detail.minsupConfigured(), detail.minsupRelativeFraction(), detail.effectiveThreshold(),
                detail.maxW(), detail.minW(), detail.meanW()).forEach(value -> assertEquals(0.0, value));
        MiningRunSummaryResponse summary = mapper.toSummaryResponse(run);
        List.of(summary.patternCount(), summary.candidateCount(), summary.totalExecutionMs(),
                summary.peakSampledJvmHeapBytes()).forEach(value -> assertEquals(0L, value));
    }

    @Test
    void shouldReturnNullForNullEntity() {
        assertNull(mapper.toSummaryResponse(null));
        assertNull(mapper.toDetailResponse(null, null));
    }

    @Test
    void shouldReplaceEveryNonNullDiagnosticWithoutChangingStoredContentOrStatus() {
        String publicMessage = "RUN có thông tin lỗi được ghi nhận; chi tiết chẩn đoán không được công bố qua API.";
        for (String raw : List.of("", " \t\r\n", "SQLException: SELECT * FROM private_table",
                "E:\\private\\datasets\\input.csv", "Bearer synthetic-test-token",
                "java.lang.IllegalStateException\n at internal.Worker.run(Worker.java:42)",
                "synthetic-user@example.invalid", "arbitrary diagnostic")) {
            for (String status : List.of("QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "LEGACY_UNVERIFIED")) {
                RunEntity run = populatedRun();
                ReflectionTestUtils.setField(run, "errorMessage", raw);
                ReflectionTestUtils.setField(run, "status", status);
                MiningRunDetailResponse detail = mapper.toDetailResponse(run, null);
                assertEquals(publicMessage, detail.errorMessage(), "Public message for status=" + status);
                assertEquals(status, detail.status());
                assertEquals(raw, run.getErrorMessage());
                assertEquals(status, run.getStatus());
            }
        }
    }

    @Test
    void shouldPreserveFailedStatusWithNoRecordedError() {
        RunEntity run = populatedRun();
        ReflectionTestUtils.setField(run, "status", "FAILED");
        MiningRunDetailResponse detail = mapper.toDetailResponse(run, null);
        assertEquals("FAILED", detail.status());
        assertNull(detail.errorMessage());
        assertNull(run.getErrorMessage());
    }

    @Test
    void shouldKeepQueuedFactoryAliasesAndUtcCompatibilityView() {
        Instant created = Instant.parse("2026-11-03T23:59:59.123456Z");
        RunEntity run = RunEntity.queued("run-new", "snapshot-independent", "WSPM_PRETREE", "CSV fixture",
                "PERCENT", 14.0, .14, .98, .8, .2, .5, created);
        assertEquals("snapshot-independent", run.getSnapshotId());
        assertEquals("QUEUED", run.getStatus());
        assertEquals("PERCENT", run.getMinsupMode());
        assertEquals(14.0, run.getMinsupConfigured());
        assertEquals(run.getMinsupRatio(), run.getMinsupRelativeFraction());
        assertEquals(.14, run.getMinsupRelativeFraction());
        assertEquals(run.getMinsupAbs(), run.getEffectiveThreshold());
        assertEquals(.98, run.getEffectiveThreshold());
        assertEquals(created, run.getCreatedAt());
        assertEquals(LocalDateTime.of(2026, 11, 3, 23, 59, 59, 123456000), run.getRunTime());
        assertNull(run.getExecutionOwnerId());
        assertNull(run.getExecutionAttemptId());
        assertNull(run.getExecutionLeaseUntil());
        MiningRunDetailResponse detail = mapper.toDetailResponse(run, null);
        assertEquals(created, detail.createdAt());
        assertEquals("PERCENT", detail.minsupMode());
        assertNull(detail.patternCount());
        assertNull(detail.totalExecutionMs());
    }

    @Test
    void shouldPreserveLegacyConstructorAndNumericCompatibilitySetters() {
        LocalDateTime wall = LocalDateTime.of(2026, 11, 3, 23, 59, 59, 123456000);
        RunEntity run = new RunEntity("legacy-id", "WSPM_PRETREE", "legacy", .14, .98, 7L, .5, wall, "legacy-path");
        assertEquals(wall, run.getRunTime());
        assertEquals(Instant.parse("2026-11-03T23:59:59.123456Z"), run.getCreatedAt());
        assertEquals("LEGACY_COMPATIBILITY", run.getMinsupMode());
        assertNull(run.getSequenceCount());
        assertNull(run.getInputSnapshot());
        run.setExecutionTime(0.0);
        run.setMemoryUsage(0.0);
        assertEquals(0L, run.getTotalExecutionMs());
        assertEquals(0L, run.getPeakSampledJvmHeapBytes());
        run.setExecutionTime(null);
        run.setMemoryUsage(null);
        assertNull(run.getTotalExecutionMs());
        assertNull(run.getPeakSampledJvmHeapBytes());
    }

    @Test
    void shouldKeepFlatJsonNumbersAndUtcTimesWithoutExposingOwnership() {
        RunEntity run = populatedRun();
        ReflectionTestUtils.setField(run, "executionOwnerId", "private-owner");
        ReflectionTestUtils.setField(run, "executionAttemptId", "private-attempt");
        ReflectionTestUtils.setField(run, "executionLeaseUntil", Instant.parse("2026-11-04T00:01:00Z"));
        // Java/JSON precision only: this does not establish JavaScript number safety.
        run.setPatternCount(9_007_199_254_740_993L);
        var json = JsonMapper.builder().build();
        for (Object response : List.of(mapper.toSummaryResponse(run), mapper.toDetailResponse(run, null))) {
            var tree = json.readTree(json.writeValueAsString(response));
            assertEquals("run-001", tree.path("runId").asString());
            assertEquals("PERCENTAGE", tree.path("minsupMode").asString());
            assertEquals("2026-11-03T23:59:59.123456Z", tree.path("createdAt").asString());
            assertEquals("2026-11-04T00:00:00.234567Z", tree.path("startedAt").asString());
            assertTrue(tree.path("patternCount").isIntegralNumber());
            assertEquals(9_007_199_254_740_993L, tree.path("patternCount").longValue());
            assertTrue(tree.path("totalExecutionMs").isIntegralNumber());
            for (String field : List.of("executionOwnerId", "executionAttemptId", "executionLeaseUntil", "configuration", "metrics")) {
                assertFalse(tree.has(field), field);
            }
        }
    }

    private RunEntity populatedRun() {
        RunEntity run = new RunEntity("run-001", "WSPM_BITPRUNE", "BENCHMARK",
                0.05, 25.0, 500L, 0.3, LocalDateTime.of(2026, 11, 3, 23, 59, 59, 123456000), null);
        // Populate persisted fields directly: the legacy constructor does not accept an independent snapshot ID.
        ReflectionTestUtils.setField(run, "snapshotId", "snap-distinct");
        ReflectionTestUtils.setField(run, "status", "SUCCEEDED");
        ReflectionTestUtils.setField(run, "minsupMode", "PERCENTAGE");
        ReflectionTestUtils.setField(run, "minsupConfigured", 5.0);
        ReflectionTestUtils.setField(run, "maxW", 0.8);
        ReflectionTestUtils.setField(run, "minW", 0.1);
        ReflectionTestUtils.setField(run, "datasetIoMs", 11L);
        ReflectionTestUtils.setField(run, "preprocessingMs", 12L);
        ReflectionTestUtils.setField(run, "dfsMiningMs", 13L);
        ReflectionTestUtils.setField(run, "completeAlgorithmMs", 25L);
        ReflectionTestUtils.setField(run, "outputIoMs", 14L);
        ReflectionTestUtils.setField(run, "startedAt", Instant.parse("2026-11-04T00:00:00.234567Z"));
        ReflectionTestUtils.setField(run, "finishedAt", Instant.parse("2026-11-04T00:00:01.345678Z"));
        run.setPatternCount(17L);
        run.setCandidateCount(91L);
        run.setExecutionTime(101.0);
        run.setMemoryUsage(123456.0);
        return run;
    }

    // A keeps the snapshot entity API unchanged; only unit fixtures need these fields.
    private static DatasetSnapshotEntity testSnapshot(String id, Long sequences, Long items, String status) {
        DatasetSnapshotEntity snapshot = new DatasetSnapshotEntity();
        ReflectionTestUtils.setField(snapshot, "snapshotId", id);
        ReflectionTestUtils.setField(snapshot, "sequenceCount", sequences);
        ReflectionTestUtils.setField(snapshot, "distinctItemCount", items);
        ReflectionTestUtils.setField(snapshot, "validationStatus", status);
        return snapshot;
    }
}
