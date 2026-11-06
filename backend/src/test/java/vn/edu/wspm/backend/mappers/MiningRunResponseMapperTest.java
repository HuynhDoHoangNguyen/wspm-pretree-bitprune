package vn.edu.wspm.backend.mappers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import vn.edu.wspm.backend.dto.miningrun.MiningRunDetailResponse;
import vn.edu.wspm.backend.dto.miningrun.MiningRunSummaryResponse;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.entities.RunEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MiningRunResponseMapperTest {

    private final MiningRunResponseMapper mapper = new MiningRunResponseMapper();

    @Test
    void shouldNotExposeAcquisitionPlaceholderCountsAsComputedStatistics() {
        DatasetSnapshotEntity snapshot = new DatasetSnapshotEntity("snap-pending", 0L, 0L, "PENDING");
        snapshot.setConversionReportJson(Map.of("statisticsStatus", "NOT_COMPUTED"));
        MiningRunDetailResponse detail = mapper.toDetailResponse(populatedRun(), snapshot);
        assertNull(detail.sequenceCount());
        assertNull(detail.distinctItemCount());
        assertEquals("PENDING", detail.validationStatus());
        assertEquals(0L, snapshot.getSequenceCount());
    }

    @Test
    void shouldPreserveExplicitlyComputedZeroStatistics() {
        DatasetSnapshotEntity snapshot = new DatasetSnapshotEntity("snap-empty", 0L, 0L, "VALID");
        snapshot.setConversionReportJson(Map.of("statisticsStatus", "COMPUTED"));
        MiningRunDetailResponse detail = mapper.toDetailResponse(populatedRun(), snapshot);
        assertEquals(0L, detail.sequenceCount());
        assertEquals(0L, detail.distinctItemCount());
    }

    @Test
    void shouldMapEveryDetailFieldAndConvertLifecycleTimesAsUtc() {
        RunEntity run = populatedRun();
        DatasetSnapshotEntity snapshot = new DatasetSnapshotEntity("snap-distinct", 500L, 42L, "VALID");

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
                new DatasetSnapshotEntity("snap-distinct", 0L, 0L, "VALID"));
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
        ReflectionTestUtils.setField(run, "startedAt", LocalDateTime.of(2026, 11, 4, 0, 0, 0, 234567000));
        ReflectionTestUtils.setField(run, "finishedAt", LocalDateTime.of(2026, 11, 4, 0, 0, 1, 345678000));
        run.setPatternCount(17L);
        run.setCandidateCount(91L);
        run.setExecutionTime(101.0);
        run.setMemoryUsage(123456.0);
        return run;
    }
}
