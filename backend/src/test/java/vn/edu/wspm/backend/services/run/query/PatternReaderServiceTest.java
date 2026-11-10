package vn.edu.wspm.backend.services.run.query;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.dto.run.result.RunResultResponse.*;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.repositories.run.*;
import vn.edu.wspm.backend.services.dataset.conversion.*;
import vn.edu.wspm.backend.services.run.PreparedRun;
import vn.edu.wspm.backend.services.run.persistence.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** New A compatibility coverage, informed by pinned TV1 reader tests. No SQL or mining. */
class PatternReaderServiceTest {
    static final String RUN = "10000000-0000-4000-8000-000000000001", ID = "18446744073709551615";
    final RunResultQueryRepository repository = mock(RunResultQueryRepository.class);
    final RunResultPublicationRepository publications = mock(RunResultPublicationRepository.class);
    final JsonMapper json = JsonMapper.builder().build();
    RunResultQueryService service;
    Map<String, Object> metadata;
    List<Map<String, Object>> temporal;

    @BeforeEach void setup() {
        service = new RunResultQueryService(repository, json, 16 * 1024 * 1024);
        service.publications(publications);
        var file = new ConversionArtifactResult.ArtifactFile("private/not-public", "a".repeat(64), 12);
        var conversion = new ConversionArtifactResult(RUN, RUN, "b".repeat(64), file, file,
            new ConversionArtifactResult.Statistics(4, 4, 0, 4, 2, 8, 1, 1, 1));
        var provenance = new RunProvenance(RunProvenance.VERSION, ConversionArtifactCodec.VERSION, ConversionArtifactCodec.POLICIES,
            new PreparedRun.Binding(conversion, "CSV", "fixture.csv", "private/raw.csv", "c".repeat(64)));
        metadata = new HashMap<>();
        metadata.put("run_id", RUN); metadata.put("snapshot_id", RUN); metadata.put("status", "SUCCEEDED");
        metadata.put("mean_w", .375); metadata.put("provenance_version", RunProvenance.VERSION);
        metadata.put("document", json.writeValueAsString(provenance)); metadata.put("publication_status", "COMMITTED");
        metadata.put("manifest_version", ResultManifestCodec.VERSION); metadata.put("result_sha256", "d".repeat(64));
        metadata.put("committed_at", "2026-11-09T10:00:00.000000Z");
        when(repository.metadata(RUN)).thenAnswer(i -> metadata);
        when(repository.counts(RUN)).thenReturn(new RunResultQueryRepository.Counts("1", "2", "2"));
        when(repository.page(eq(RUN), anyInt(), anyInt())).thenReturn(List.of(pattern()));
        when(repository.pattern(RUN, ID)).thenReturn(pattern());
        temporal = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            var row = new HashMap<String, Object>();
            row.put("set_order", String.valueOf(i)); row.put("item_order", "1"); row.put("item_id", String.valueOf(i));
            row.put("product_id", "product-" + i); row.put("product_name_snapshot", "Historical name");
            row.put("sku_snapshot", "Historical SKU"); row.put("item_weight", i * .25);
            row.put("occurrences", "9007199254740993"); row.put("support", "2"); temporal.add(row);
        }
        when(repository.temporal(RUN, ID, 100001)).thenReturn(temporal);
    }
    static PatternSummary pattern() {
        String canonical = "<(1)(2)>";
        return new PatternSummary(ID, RUN, canonical, ConversionArtifactCodec.sha256(canonical.getBytes(StandardCharsets.UTF_8)),
            "2", .5, .75, .375, PatternWeightPolicy.VERSION, 2, 2);
    }
    void rejects(ErrorCode code, Runnable work) {
        assertEquals(code, assertThrows(ApplicationException.class, work::run).errorCode());
    }
    @Test void defaultPublishedPageIsOneBasedAndManifestVerified() {
        var result = service.page(RUN, null, null, null);
        assertEquals(1, result.page()); assertEquals("1", result.total());
        assertEquals(ID, result.items().getFirst().patternId());
        assertTrue(result.result().publishedReadable());
        verify(publications).verified(RUN); verify(repository).page(RUN, 0, 20);
    }
    @Test void secondPageUsesOffsetWithoutChangingRun() {
        service.page(RUN, "published", "2", "20"); verify(repository).page(RUN, 20, 20);
    }
    @Test void detailPreservesItemsetsWeightsAndHistoricalSnapshots() {
        var result = service.detail(RUN, ID, "published");
        assertEquals(2, result.itemsets().size()); assertEquals("<(1)(2)>", result.pattern().canonicalPattern());
        assertEquals(.375, result.pattern().patternWeight());
        assertEquals("9007199254740993", result.itemsets().getFirst().items().getFirst().occurrenceCount());
        assertEquals("Historical name", result.itemsets().getFirst().items().getFirst().productNameSnapshot());
        assertFalse(json.writeValueAsString(result).contains("private/"));
    }
    @Test void succeededWithoutPublicationDoesNotEnablePublishedRead() {
        metadata.put("publication_status", "NOT_PUBLISHED");
        rejects(ErrorCode.INVALID_RESOURCE_STATE, () -> service.page(RUN, "published", "1", "20"));
        verify(repository, never()).page(anyString(), anyInt(), anyInt());
    }
    @Test void corruptManifestStopsBeforePatternRead() {
        when(publications.verified(RUN)).thenThrow(new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE));
        rejects(ErrorCode.INVALID_RESOURCE_STATE, () -> service.page(RUN, "published", "1", "20"));
        verify(repository, never()).page(anyString(), anyInt(), anyInt());
    }
    @Test void patternFromAnotherRunReturnsNotFound() {
        when(repository.pattern(RUN, ID)).thenReturn(null);
        rejects(ErrorCode.RESOURCE_NOT_FOUND, () -> service.detail(RUN, ID, "published"));
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "100001"})
    void invalidPageNeverReadsStorage(String page) {
        rejects(ErrorCode.VALIDATION_FAILED, () -> service.page(RUN, "published", page, "20"));
        verifyNoInteractions(publications); verify(repository, never()).metadata(anyString());
    }
    @Test void publishedRunCannotBeRelabelledStaging() {
        rejects(ErrorCode.INVALID_RESOURCE_STATE, () -> service.page(RUN, "staging", "1", "20"));
    }
    @Test void missingHistoricalSnapshotIsRejected() {
        temporal.getFirst().remove("product_id");
        rejects(ErrorCode.INVALID_RESOURCE_STATE, () -> service.detail(RUN, ID, "published"));
    }
    @Test void changedTemporalBoundaryIsRejected() {
        temporal.get(1).put("set_order", "1"); temporal.get(1).put("item_order", "2");
        rejects(ErrorCode.INVALID_RESOURCE_STATE, () -> service.detail(RUN, ID, "published"));
    }
    @Test void storedWeightedSupportMismatchIsRejected() {
        var p = pattern();
        when(repository.pattern(RUN, ID)).thenReturn(new PatternSummary(ID, RUN, p.canonicalPattern(), p.canonicalPatternHash(),
            "2", .5, .8, .375, PatternWeightPolicy.VERSION, 2, 2));
        rejects(ErrorCode.INVALID_RESOURCE_STATE, () -> service.detail(RUN, ID, "published"));
    }
    @Test void emptyPublishedResultIsExplicitlyEmpty() {
        when(repository.counts(RUN)).thenReturn(new RunResultQueryRepository.Counts("0", "0", "2"));
        when(repository.page(RUN, 0, 20)).thenReturn(List.of());
        var result = service.page(RUN, "published", "1", "20");
        assertEquals("0", result.total()); assertTrue(result.items().isEmpty()); assertFalse(result.result().eligibleForCombo());
    }
    @Test void databaseFailureIsTranslatedWithoutLeakingSql() {
        when(repository.metadata(RUN)).thenThrow(new DataAccessResourceFailureException("secret connection text"));
        rejects(ErrorCode.SERVICE_UNAVAILABLE, () -> service.metadata(RUN));
    }
    @ParameterizedTest @ValueSource(strings = {"01", "18446744073709551616"})
    void malformedOrOverflowPatternIdNeverQueriesDatabase(String id) {
        rejects(ErrorCode.VALIDATION_FAILED, () -> service.detail(RUN, id, "published"));
        verify(repository, never()).metadata(anyString());
    }
}
