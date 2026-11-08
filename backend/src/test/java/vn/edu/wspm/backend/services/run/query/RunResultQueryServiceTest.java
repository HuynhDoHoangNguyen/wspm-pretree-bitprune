package vn.edu.wspm.backend.services.run.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.dto.run.result.RunResultResponse.*;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.repositories.run.RunResultQueryRepository;
import vn.edu.wspm.backend.services.dataset.conversion.*;
import vn.edu.wspm.backend.services.run.PreparedRun;
import vn.edu.wspm.backend.services.run.persistence.*;

class RunResultQueryServiceTest {
    static final String RUN = "10000000-0000-4000-8000-000000000001", ID = "18446744073709551615";
    final RunResultQueryRepository repository = mock(RunResultQueryRepository.class);
    final JsonMapper json = JsonMapper.builder().build();
    RunResultQueryService service;
    Map<String, Object> metadata;
    @BeforeEach void setup() {
        service = new RunResultQueryService(repository, json, 16 * 1024 * 1024);
        metadata = metadata();
        when(repository.metadata(RUN)).thenAnswer(i -> metadata);
        when(repository.counts(RUN)).thenReturn(new RunResultQueryRepository.Counts("1", "2", "2"));
        when(repository.page(eq(RUN), anyInt(), anyInt())).thenReturn(List.of(parent()));
        when(repository.pattern(RUN, ID)).thenReturn(parent());
        when(repository.temporal(RUN, ID, 100001)).thenReturn(rows());
    }
    static RunProvenance provenance() {
        var file = new ConversionArtifactResult.ArtifactFile("private/never-expose", "a".repeat(64), 12);
        var p = new ConversionArtifactResult(RUN, RUN, "b".repeat(64), file, file,
                new ConversionArtifactResult.Statistics(4, 4, 0, 4, 2, 8, 1, 1, 1));
        return new RunProvenance(RunProvenance.VERSION, ConversionArtifactCodec.VERSION, ConversionArtifactCodec.POLICIES,
                new PreparedRun.Binding(p, "CSV", "fixture.csv", "private/raw.csv", "c".repeat(64)));
    }
    Map<String, Object> metadata() {
        var row = new HashMap<String, Object>();
        row.put("run_id", RUN); row.put("snapshot_id", RUN); row.put("status", "SUCCEEDED"); row.put("mean_w", .375);
        row.put("provenance_version", RunProvenance.VERSION); row.put("document", json.writeValueAsString(provenance()));
        row.put("publication_status", "NOT_PUBLISHED"); return row;
    }
    static PatternSummary parent() {
        String canonical = "<(1 2)>";
        return new PatternSummary(ID, RUN, canonical, ConversionArtifactCodec.sha256(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "2", .5, .75, .375, PatternWeightPolicy.VERSION, 1, 2);
    }
    static List<Map<String, Object>> rows() {
        var rows = new ArrayList<Map<String, Object>>();
        for (int id = 1; id <= 2; id++) {
            var row = new HashMap<String, Object>();
            row.put("set_order", "1"); row.put("item_order", Integer.toString(id)); row.put("item_id", Integer.toString(id));
            row.put("product_id", "p" + id); row.put("product_name_snapshot", "Historical name"); row.put("sku_snapshot", "Historical SKU");
            row.put("item_weight", id * .25); row.put("occurrences", "9007199254740993"); row.put("support", "2"); rows.add(row);
        }
        return rows;
    }
    @Test void exactIdsStatisticsAndPrivateProvenanceArePreservedOrRedacted() {
        var detail = service.detail(RUN, ID, "staging");
        assertEquals(ID, detail.pattern().patternId()); assertEquals("9007199254740993", detail.itemsets().getFirst().items().getFirst().occurrenceCount());
        String response = json.writeValueAsString(detail);
        assertFalse(response.contains("private/")); assertFalse(response.contains("rawReference"));
        assertFalse(detail.result().publishedReadable()); assertFalse(detail.result().eligibleForCombo());
        assertEquals("UNVERIFIED", detail.result().completeness());
    }
    @Test void defaultPublishedReadIsRejectedAndNeverQueriesPatterns() {
        assertCode(ErrorCode.INVALID_RESOURCE_STATE, () -> service.page(RUN, null, null, null));
        verify(repository, never()).page(anyString(), anyInt(), anyInt());
    }
    @Test void legacyIsMetadataOnlyAndPartialPairIsCorruption() {
        metadata.remove("provenance_version"); metadata.remove("document"); metadata.remove("publication_status");
        assertEquals("UNVERIFIED", service.metadata(RUN).publicationStatus());
        assertCode(ErrorCode.INVALID_RESOURCE_STATE, () -> service.detail(RUN, ID, "staging"));
        metadata.put("publication_status", "NOT_PUBLISHED");
        assertCode(ErrorCode.INVALID_RESOURCE_STATE, () -> service.metadata(RUN));
    }
    @Test void committedMarkerWithoutVerifierCannotEnablePublishedOrStagingReads() {
        metadata.put("publication_status", "COMMITTED"); metadata.put("manifest_version", "FUTURE_MANIFEST");
        assertFalse(service.metadata(RUN).publishedReadable());
        assertCode(ErrorCode.INVALID_RESOURCE_STATE, () -> service.export(RUN, "published"));
        assertCode(ErrorCode.INVALID_RESOURCE_STATE, () -> service.export(RUN, "staging"));
    }
    @ParameterizedTest @ValueSource(strings = {"QUEUED", "RUNNING", "FAILED", "LEGACY_UNVERIFIED"})
    void onlySucceededRunCanBeInspected(String status) {
        metadata.put("status", status); assertFalse(service.metadata(RUN).stagingReadable());
        assertCode(ErrorCode.INVALID_RESOURCE_STATE, () -> service.page(RUN, "staging", null, null));
    }
    @ParameterizedTest @ValueSource(strings = {"", "0", "-1", "01", "+1", "1.0", "18446744073709551616", " 1", "1 OR 1=1"})
    void rejectsNoncanonicalOrOverflowPatternIds(String id) {
        assertCode(ErrorCode.VALIDATION_FAILED, () -> service.detail(RUN, id, "staging"));
        verifyNoInteractions(repository);
    }
    @ParameterizedTest @ValueSource(strings = {"1", "9007199254740993", "9223372036854775808", "18446744073709551615"})
    void acceptsEntirePositiveUnsignedIdRange(String id) { assertEquals(id, RunResultQueryService.decimalId(id)); }
    @Test void validatesPaginationAndViewBeforeDatabaseAccess() {
        assertCode(ErrorCode.VALIDATION_FAILED, () -> service.page(RUN, "staging", "0", null));
        assertCode(ErrorCode.VALIDATION_FAILED, () -> service.page(RUN, "staging", null, "101"));
        assertCode(ErrorCode.VALIDATION_FAILED, () -> service.page(RUN, "Staging", null, null)); verifyNoInteractions(repository);
    }
    @Test void scopedLookupDoesNotDiscloseAnotherRunPattern() {
        when(repository.pattern(RUN, ID)).thenReturn(null);
        assertCode(ErrorCode.RESOURCE_NOT_FOUND, () -> service.detail(RUN, ID, "staging"));
    }
    @Test void malformedProvenanceAndUnknownVersionReject() {
        metadata.put("document", "{}"); assertCode(ErrorCode.INVALID_RESOURCE_STATE, () -> service.metadata(RUN));
        metadata.put("document", json.writeValueAsString(provenance())); metadata.put("provenance_version", "UNKNOWN");
        assertCode(ErrorCode.INVALID_RESOURCE_STATE, () -> service.metadata(RUN));
    }
    @ParameterizedTest @ValueSource(strings = {"item_order", "item_id", "set_order"})
    void invalidTemporalCoordinatesRejectWithoutCanonicalization(String key) {
        var rows = rows(); rows.getFirst().put(key, "2");
        when(repository.temporal(RUN, ID, 100001)).thenReturn(rows);
        assertCode(ErrorCode.INVALID_RESOURCE_STATE, () -> service.detail(RUN, ID, "staging"));
    }
    @Test void missingSnapshotsAndChangedWeightsReject() {
        var rows = rows(); rows.getFirst().remove("product_id");
        when(repository.temporal(RUN, ID, 100001)).thenReturn(rows);
        assertCode(ErrorCode.INVALID_RESOURCE_STATE, () -> service.detail(RUN, ID, "staging"));
        rows.getFirst().put("product_id", "p1"); rows.getFirst().put("item_weight", .5);
        assertCode(ErrorCode.INVALID_RESOURCE_STATE, () -> service.detail(RUN, ID, "staging"));
    }
    @Test void intOverflowIsRejectedRatherThanNarrowed() {
        var rows = rows(); rows.getFirst().put("item_id", "4294967295");
        when(repository.temporal(RUN, ID, 100001)).thenReturn(rows);
        assertCode(ErrorCode.INVALID_RESOURCE_STATE, () -> service.detail(RUN, ID, "staging"));
    }
    @Test void metadataCountsNeverImplyCompleteEmptyOutput() {
        when(repository.counts(RUN)).thenReturn(new RunResultQueryRepository.Counts("0", "0", "2"));
        when(repository.page(eq(RUN), anyInt(), anyInt())).thenReturn(List.of());
        var page = service.page(RUN, "staging", null, null);
        assertTrue(page.items().isEmpty()); assertEquals("UNVERIFIED", page.result().completeness());
        assertEquals(0, json.readTree(service.export(RUN, "staging")).get("patterns").size());
    }
    @Test void exportIsVersionedAndByteOrRowLimitsNeverReturnPartialSuccess() {
        var export = json.readTree(service.export(RUN, "staging"));
        assertEquals("WDM_RESULT_EXPORT_V1", export.get("formatVersion").asString());
        assertEquals(ID, export.get("patterns").get(0).get("pattern").get("patternId").asString());
        var bounded = new RunResultQueryService(repository, json, 256);
        assertCode(ErrorCode.PAYLOAD_TOO_LARGE, () -> bounded.export(RUN, "staging"));
        when(repository.counts(RUN)).thenReturn(new RunResultQueryRepository.Counts("10001", "2", "2"));
        assertCode(ErrorCode.PAYLOAD_TOO_LARGE, () -> service.export(RUN, "staging"));
    }
    @Test void deadlineRejectsBeforeReturningAFileWithoutWaitingInTheTest() {
        var ticks = new java.util.concurrent.atomic.AtomicLong();
        var bounded = new RunResultQueryService(repository, json, 16777216, () -> ticks.getAndAdd(6_000_000_000L));
        assertCode(ErrorCode.SERVICE_UNAVAILABLE, () -> bounded.export(RUN, "staging"));
    }
    @Test void nonfiniteMeanAndUnsupportedSupportAreNotSilentlyCoerced() {
        metadata.put("mean_w", Double.NaN);
        assertCode(ErrorCode.INVALID_RESOURCE_STATE, () -> service.metadata(RUN)); metadata.put("mean_w", .375);
        var p = parent();
        when(repository.pattern(RUN, ID)).thenReturn(new PatternSummary(ID, RUN, p.canonicalPattern(), p.canonicalPatternHash(),
                "9007199254740993", .5, .75, .375, p.patternWeightPolicy(), 1, 2));
        assertCode(ErrorCode.INVALID_RESOURCE_STATE, () -> service.detail(RUN, ID, "staging"));
    }
    @Test void databaseFailureDoesNotExposeSqlOrPrivateValues() {
        when(repository.metadata(RUN)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private SQL with secret value"));
        var failure = assertThrows(ApplicationException.class, () -> service.metadata(RUN));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, failure.errorCode()); assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains("private"));
    }
    static void assertCode(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code.name(), assertThrows(ApplicationException.class, action).code());
    }
}
