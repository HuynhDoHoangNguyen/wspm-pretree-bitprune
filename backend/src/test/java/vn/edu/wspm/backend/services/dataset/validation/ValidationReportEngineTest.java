package vn.edu.wspm.backend.services.dataset.validation;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.dto.dataset.ValidationResponse;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.services.dataset.csv.ProductReferenceLookup;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

class ValidationReportEngineTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC);
    static final ValidationLimits LIMITS = new ValidationLimits(16384, 100000, 8388608, 16777216, 30000, 2);
    private final ProductReferenceLookup catalog = mock(ProductReferenceLookup.class);
    private final JsonMapper mapper = JsonMapper.builder().build();

    ValidationReportEngineTest() {
        when(catalog.findExisting(anySet())).thenReturn(Set.of("product-laptop", "product-mouse", "product-bag"));
    }

    static byte[] fixture(String name) throws Exception {
        try (var stream = ValidationReportEngineTest.class.getResourceAsStream("/wdm/csv-v1/" + name)) {
            assertNotNull(stream);
            return stream.readAllBytes();
        }
    }

    private ValidationReport generate(byte[] raw, ValidationLimits limits) throws Exception {
        return new ValidationReportEngine(catalog, limits, CLOCK).generate("snapshot", "hash", Integer.toString(raw.length), raw, () -> {});
    }

    @Test
    void validFixtureCountsRepresentRecordsAndOrdersNotIssueCount() throws Exception {
        var report = generate(fixture("valid.csv"), LIMITS);
        assertEquals("VALID", report.validationStatus());
        assertEquals("READY", report.cleaningOutcome());
        assertTrue(report.countsComplete());
        assertEquals("5", report.counts().inputRecords());
        assertEquals("3", report.counts().inputOrders());
        assertEquals("5", report.counts().keptRecords());
        assertEquals("3", report.counts().keptOrders());
        assertEquals("0", report.counts().issueCount());
        assertEquals(5, report.manifest().size());
        verify(catalog, times(1)).findExisting(anySet());
    }

    @Test
    void dirtyRawCanHaveValidCleanSubsetAndCompleteManifest() throws Exception {
        var report = generate(fixture("business-invalid.csv"), LIMITS);
        assertEquals("VALID", report.validationStatus());
        assertEquals("1", report.counts().keptRecords());
        assertEquals("3", report.counts().rejectedRecords());
        assertEquals("1", report.counts().deduplicatedRecords());
        assertEquals("1", report.counts().keptOrders());
        assertEquals("2", report.counts().rejectedOrders());
        assertEquals("1", report.counts().businessInvalidRecords());
        assertEquals(3, report.diagnostics().size());
        assertEquals(5, report.manifest().size());
        assertEquals(5, report.manifest().get(4).representative().recordNumber());
        assertFalse(mapper.writeValueAsString(report).contains("order-unknown"));
        assertFalse(mapper.writeValueAsString(report).contains("product-missing"));
    }

    @Test
    void emptyInputAndUnknownOrderAreInvalidWithoutInventingOrderCounts() throws Exception {
        var empty = generate(fixture("empty.csv"), LIMITS);
        assertEquals("INVALID", empty.validationStatus());
        assertEquals("EMPTY_INPUT", empty.cleaningOutcome());
        assertEquals("0", empty.counts().inputOrders());
        String original = new String(fixture("business-invalid.csv"), StandardCharsets.UTF_8);
        var blocked = generate(original.replace("order-unknown,", " ,").getBytes(StandardCharsets.UTF_8), LIMITS);
        assertEquals("INVALID", blocked.validationStatus());
        assertEquals("BLOCKED_UNATTRIBUTABLE_ORDER", blocked.cleaningOutcome());
        assertNull(blocked.counts().inputOrders());
        assertNull(blocked.counts().rejectedOrders());
        assertEquals("2", blocked.counts().knownOrders());
        assertEquals("5", blocked.counts().blockedRecords());
    }

    @Test
    void malformedLateRecordNeverReportsPrefixAsFullDatasetOrCallsCatalog() throws Exception {
        byte[] prefix = fixture("valid.csv");
        byte[] suffix = "malformed-private-record\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] raw = Arrays.copyOf(prefix, prefix.length + suffix.length);
        System.arraycopy(suffix, 0, raw, prefix.length, suffix.length);
        var report = generate(raw, LIMITS);
        assertEquals("INVALID", report.validationStatus());
        assertFalse(report.countsComplete());
        assertEquals("5", report.counts().parsedRecords());
        assertNull(report.counts().inputRecords());
        assertNull(report.counts().keptRecords());
        assertNull(report.counts().inputOrders());
        assertTrue(report.manifest().isEmpty());
        assertEquals("CSV_COLUMN_COUNT_MISMATCH", report.diagnostics().getFirst().code());
        assertFalse(mapper.writeValueAsString(report).contains("private-record"));
        verifyNoInteractions(catalog);
    }

    @Test
    void invalidUtf8UsesCursorAccuracyRatherThanClaimingExactBytePosition() throws Exception {
        var report = generate(new byte[]{(byte) 0xff}, LIMITS);
        assertEquals("DECODER_CURSOR", report.diagnostics().getFirst().positionAccuracy());
        assertFalse(report.countsComplete());
        assertEquals("INVALID", report.validationStatus());
    }

    @Test
    void recordRowRawAndDeadlineBudgetsAreOperationalNotInvalidData() throws Exception {
        byte[] raw = fixture("valid.csv");
        var rowBound = new ValidationLimits(16384, 1, 8388608, 16777216, 30000, 2);
        var recordBound = new ValidationLimits(1, 100, 8388608, 16777216, 30000, 2);
        var rawBound = new ValidationLimits(16384, 100, 8388608, 1, 30000, 2);
        for (var limits : new ValidationLimits[]{rowBound, recordBound, rawBound}) {
            assertEquals(503, assertThrows(ApplicationException.class, () -> generate(raw, limits)).status().value());
        }
        var timeout = ValidationReportEngine.unavailable();
        assertSame(timeout, assertThrows(ApplicationException.class, () -> new ValidationReportEngine(catalog, LIMITS, CLOCK)
                .generate("id", "sha", "1", raw, () -> { throw timeout; })));
        verifyNoInteractions(catalog);
    }

    @Test
    void catalogFailurePropagatesWithoutUnknownProductDiagnostics() throws Exception {
        var failure = new DataAccessResourceFailureException("private-database-detail");
        when(catalog.findExisting(anySet())).thenThrow(failure);
        byte[] raw = fixture("valid.csv");
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> generate(raw, LIMITS)));
    }

    @Test
    void jsonReadbackAndPublicPaginationPreserveSafeFieldsButHideManifest() throws Exception {
        var report = generate(fixture("business-invalid.csv"), LIMITS);
        Map<?, ?> persisted = mapper.convertValue(report, Map.class);
        assertEquals(report, mapper.convertValue(persisted, ValidationReport.class));
        var page = ValidationResponse.page(report, 1, 1);
        assertEquals("3", page.issues().totalElements());
        assertEquals(report.diagnostics().get(1), page.issues().content().getFirst());
        assertTrue(ValidationResponse.page(report, Integer.MAX_VALUE, 100).issues().content().isEmpty());
        assertFalse(mapper.writeValueAsString(page).contains("manifest"));
        assertFalse(mapper.writeValueAsString(page).contains("order-duplicate"));
        assertThrows(UnsupportedOperationException.class, () -> page.issues().content().clear());
    }
}
