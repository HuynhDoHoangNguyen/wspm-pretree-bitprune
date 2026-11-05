package vn.edu.wspm.backend.services.dataset.conversion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.repositories.dataset.SnapshotValidationRepository;
import vn.edu.wspm.backend.services.dataset.RawSnapshotStorage;
import vn.edu.wspm.backend.services.dataset.validation.*;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConversionValidationGateTest {
    private static final String ID = "00000000-0000-0000-0000-000000000001";
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final SnapshotValidationRepository repository = mock(SnapshotValidationRepository.class);
    private final RawSnapshotStorage storage = mock(RawSnapshotStorage.class);
    private final ValidationLimits limits = new ValidationLimits(16384, 100000, 8388608, 16777216, 30000, 2);

    private record Fixture(DatasetSnapshotEntity entity, byte[] raw) {}
    @SuppressWarnings("unchecked")
    private Fixture fixture(String name) throws Exception {
        byte[] raw;
        try (var stream = getClass().getResourceAsStream("/wdm/csv-v1/" + name)) {
            assertNotNull(stream); raw = stream.readAllBytes();
        }
        var entity = new DatasetSnapshotEntity();
        entity.setSnapshotId(ID); entity.setSourceType("CSV"); entity.setValidationStatus("VALID");
        entity.setSequenceCount(0L); entity.setRawFilePath(ID + "/raw.csv");
        entity.setRawSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        var engine = new ValidationReportEngine(ids -> Set.of("product-laptop", "product-mouse", "product-bag"), limits,
                Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC));
        var report = engine.generate(ID, entity.getRawSha256(), Integer.toString(raw.length), raw, () -> {});
        entity.setConversionReportJson(new LinkedHashMap<>(Map.of("contractVersion", "wdm-order-csv-v1",
                "conversionStatus", "NOT_STARTED", "statisticsStatus", "NOT_COMPUTED",
                "provenance", Map.of("rawSizeBytes", Integer.toString(raw.length)),
                "validation", mapper.convertValue(report, Map.class))));
        when(repository.find(ID)).thenReturn(Optional.of(entity));
        doReturn(raw).when(storage).verifiedBytes(eq(ID), anyString(), anyString(), anyString());
        return new Fixture(entity, raw);
    }
    private ConversionValidationGate gate() { return new ConversionValidationGate(repository, storage, limits, mapper); }
    @SuppressWarnings("unchecked") private Map<String, Object> report(Fixture fixture) {
        return (Map<String, Object>) fixture.entity().getConversionReportJson().get("validation");
    }
    @SuppressWarnings("unchecked") private Map<String, Object> counts(Fixture fixture) { return (Map<String, Object>) report(fixture).get("counts"); }
    @SuppressWarnings("unchecked") private List<Map<String, Object>> manifest(Fixture fixture) { return (List<Map<String, Object>>) report(fixture).get("manifest"); }
    @SuppressWarnings("unchecked") private List<Map<String, Object>> diagnostics(Fixture fixture) { return (List<Map<String, Object>>) report(fixture).get("diagnostics"); }
    private void denied(int status) { assertEquals(status, assertThrows(ApplicationException.class, () -> gate().prepare(ID)).status().value()); }

    @ParameterizedTest
    @ValueSource(strings = {"valid.csv", "business-invalid.csv"})
    void realAcceptedPipelineReportsAdmitExactRetainedRecordsWithoutRevalidationOrMutation(String name) throws Exception {
        var fixture = fixture(name);
        String before = mapper.writeValueAsString(fixture.entity().getConversionReportJson());
        var proof = gate().prepare(ID);
        assertEquals(name.equals("valid.csv") ? 5 : 1, proof.keptRecords().size());
        assertArrayEquals(fixture.raw(), proof.rawBytes());
        byte[] copy = proof.rawBytes(); copy[0] ^= 1;
        assertArrayEquals(fixture.raw(), proof.rawBytes());
        byte original = fixture.raw()[0]; fixture.raw()[0] ^= 1;
        assertEquals(original, proof.rawBytes()[0]);
        assertThrows(UnsupportedOperationException.class, () -> proof.keptRecords().clear());
        assertFalse(proof.toString().contains("anon-A"));
        assertEquals(before, mapper.writeValueAsString(fixture.entity().getConversionReportJson()));
        assertEquals("VALID", fixture.entity().getValidationStatus());
        verify(repository, never()).publish(anyString(), anyString(), anyString(), anyMap(), anyMap(), anyString());
    }

    @ParameterizedTest @ValueSource(strings = {"PENDING", "INVALID", "LEGACY_UNVERIFIED", "UNKNOWN"})
    void nonValidStatesAreDeniedBeforeFileAccess(String state) throws Exception {
        var fixture = fixture("valid.csv"); fixture.entity().setValidationStatus(state);
        denied(409); verifyNoInteractions(storage);
    }

    @ParameterizedTest @ValueSource(strings = {"reportVersion", "policyVersion", "contractVersion", "snapshotId", "rawSha256", "rawSizeBytes", "completedAt"})
    void unsupportedOrMismatchedReportBindingIsDeniedBeforeFileAccess(String field) throws Exception {
        var fixture = fixture("valid.csv"); report(fixture).put(field, "unsupported-or-mismatched");
        denied(409); verifyNoInteractions(storage);
    }

    @Test
    void validColumnAloneMissingMalformedOrNotReadyReportCannotBypassGate() throws Exception {
        for (Object report : List.of("not-a-report", Map.of(), Map.of("manifest", List.of()))) {
            var fixture = fixture("valid.csv"); fixture.entity().getConversionReportJson().put("validation", report); denied(409);
        }
        var fixture = fixture("valid.csv"); fixture.entity().getConversionReportJson().remove("validation"); denied(409);
        fixture = fixture("valid.csv"); report(fixture).put("countsComplete", false); denied(409);
        fixture = fixture("valid.csv"); report(fixture).put("cleaningOutcome", "EMPTY_AFTER_CLEANING"); denied(409);
        fixture = fixture("valid.csv"); report(fixture).put("manifest", null); denied(409);
        verifyNoInteractions(storage);
    }

    @Test
    void preexistingConversionArtifactsAndIncompatibleMetadataAreDenied() throws Exception {
        var fixture = fixture("valid.csv"); fixture.entity().setSequenceSha256("a".repeat(64)); denied(409);
        fixture = fixture("valid.csv"); fixture.entity().setMappingFilePath("private-mapping"); denied(409);
        fixture = fixture("valid.csv"); fixture.entity().setSequenceCount(1L); denied(409);
        fixture = fixture("valid.csv"); fixture.entity().getConversionReportJson().put("conversionStatus", "RUNNING"); denied(409);
        fixture = fixture("valid.csv"); fixture.entity().getConversionReportJson().put("statisticsStatus", "COMPUTED"); denied(409);
        fixture = fixture("valid.csv"); fixture.entity().setSourceType("LEGACY"); denied(409);
        verifyNoInteractions(storage);
    }

    @ParameterizedTest @ValueSource(strings = {"parsedRecords", "inputRecords", "inputOrders", "knownOrders", "businessInvalidRecords",
            "keptRecords", "rejectedRecords", "deduplicatedRecords", "blockedRecords", "keptOrders", "rejectedOrders", "issueCount"})
    void countersMustAgreeWithCompleteRawAndManifest(String field) throws Exception {
        var fixture = fixture("business-invalid.csv"); counts(fixture).put(field, "999999999999999999999999"); denied(409);
        counts(fixture).put(field, null); denied(409);
    }

    @Test
    void manifestMustCoverUniqueCorrectPositionsAndKnownActions() throws Exception {
        var fixture = fixture("valid.csv"); manifest(fixture).remove(0); denied(409);
        fixture = fixture("valid.csv"); manifest(fixture).add(manifest(fixture).getFirst()); denied(409);
        fixture = fixture("valid.csv"); manifest(fixture).getFirst().put("position", Map.of("recordNumber", 2, "startLine", 99, "endLine", 99)); denied(409);
        fixture = fixture("valid.csv"); manifest(fixture).getFirst().put("action", "UNRECOGNIZED"); denied(409);
        fixture = fixture("valid.csv"); manifest(fixture).getFirst().put("representative", null); denied(409);
        fixture = fixture("valid.csv"); manifest(fixture).getFirst().put("action", "BLOCKED"); denied(409);
    }

    @Test
    void duplicateMustReferenceEarlierKeptIdenticalPayloadNotRejectedOrAnotherLine() throws Exception {
        var fixture = fixture("business-invalid.csv");
        manifest(fixture).get(4).put("representative", manifest(fixture).getFirst().get("position")); denied(409);
        fixture = fixture("business-invalid.csv");
        manifest(fixture).get(4).put("action", "KEPT");
        manifest(fixture).get(4).put("representative", manifest(fixture).get(4).get("position"));
        manifest(fixture).get(4).put("reasons", List.of());
        counts(fixture).put("keptRecords", "2"); counts(fixture).put("deduplicatedRecords", "0"); denied(409);
        fixture = fixture("valid.csv");
        manifest(fixture).get(1).put("action", "DEDUPLICATED");
        manifest(fixture).get(1).put("representative", manifest(fixture).getFirst().get("position"));
        manifest(fixture).get(1).put("reasons", List.of("IDENTICAL_LINE_REPETITION")); denied(409);
    }

    @Test
    void partialOrderRetentionAndContradictoryDiagnosticsCannotPass() throws Exception {
        var fixture = fixture("valid.csv");
        manifest(fixture).getFirst().put("action", "REJECTED_ORDER");
        manifest(fixture).getFirst().put("representative", null);
        manifest(fixture).getFirst().put("reasons", List.of("BUSINESS_RULE_FAILURE")); denied(409);
        fixture = fixture("business-invalid.csv"); diagnostics(fixture).getFirst().put("field", null); denied(409);
        fixture = fixture("business-invalid.csv"); diagnostics(fixture).getFirst().put("stage", "UNKNOWN"); denied(409);
        fixture = fixture("business-invalid.csv"); diagnostics(fixture).getFirst().put("positions", List.of(manifest(fixture).get(3).get("position"))); denied(409);
    }

    @Test
    void staleStateOrReportDuringRawReadCannotBeAdmitted() throws Exception {
        var fixture = fixture("valid.csv");
        doAnswer(call -> { fixture.entity().getConversionReportJson().put("conversionStatus", "CHANGED"); return fixture.raw(); })
                .when(storage).verifiedBytes(anyString(), anyString(), anyString(), anyString()); denied(409);
        var other = fixture("valid.csv");
        doAnswer(call -> { report(other).put("completedAt", "2026-11-05T00:00:01Z"); return other.raw(); })
                .when(storage).verifiedBytes(anyString(), anyString(), anyString(), anyString()); denied(409);
    }

    @Test
    void storageDatabaseAndBudgetFailuresAreOperationalWithoutChangingValidation() throws Exception {
        var fixture = fixture("valid.csv");
        doThrow(new IOException("private-path")).when(storage).verifiedBytes(anyString(), anyString(), anyString(), anyString());
        var failure = assertThrows(ApplicationException.class, () -> gate().prepare(ID));
        assertEquals(503, failure.status().value()); assertFalse(failure.getMessage().contains("private-path"));
        assertEquals("VALID", fixture.entity().getValidationStatus());
        doReturn(fixture.raw()).when(storage).verifiedBytes(anyString(), anyString(), anyString(), anyString());
        var tiny = new ValidationLimits(1, 100000, 8388608, 16777216, 30000, 2);
        assertEquals(503, assertThrows(ApplicationException.class,
                () -> new ConversionValidationGate(repository, storage, tiny, mapper).prepare(ID)).status().value());
        when(repository.find(ID)).thenThrow(new IllegalStateException("private-db")); denied(503);
    }

    @Test
    void idsAndMissingSnapshotsAreExplicitAndCapacityIsReleased() throws Exception {
        assertEquals(400, assertThrows(ApplicationException.class, () -> gate().prepare("../bad")).status().value());
        when(repository.find(ID)).thenReturn(Optional.empty()); denied(404);
        fixture("valid.csv");
        var gate = gate(); assertFalse(gate.prepare(ID).keptRecords().isEmpty());
    }

    @Test
    void boundedConcurrencyRejectsExcessWorkAndRestoresPermitAfterCompletion() throws Exception {
        var fixture = fixture("valid.csv"); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return fixture.raw(); })
                .when(storage).verifiedBytes(anyString(), anyString(), anyString(), anyString());
        var one = new ValidationLimits(16384, 100000, 8388608, 16777216, 30000, 1);
        var gate = new ConversionValidationGate(repository, storage, one, mapper);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(() -> gate.prepare(ID));
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                assertEquals(503, assertThrows(ApplicationException.class, () -> gate.prepare(ID)).status().value());
            } finally { release.countDown(); }
            assertEquals(5, pending.get(10, TimeUnit.SECONDS).keptRecords().size());
        }
        assertEquals(5, gate.prepare(ID).keptRecords().size());
    }
}
