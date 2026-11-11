package vn.edu.wspm.backend.dto;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import vn.edu.wspm.backend.mappers.ValidationResponseMapper;
import vn.edu.wspm.backend.services.dataset.validation.ValidationReportPresenter;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.dto.dataset.ValidationResponse;
import vn.edu.wspm.backend.services.dataset.csv.OrderConsistencyIssue.Position;
import vn.edu.wspm.backend.services.dataset.validation.ValidationReport;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic projection fixtures only: not evidence of validation or conversion eligibility. */
class ValidationResponseContractTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ValidationReportPresenter presenter = new ValidationReportPresenter(Mappers.getMapper(ValidationResponseMapper.class));

    private ValidationReport report() {
        var first = new Position(2, 2, 4);
        var second = new Position(3, 5, 5);
        return new ValidationReport("WDM_VALIDATION_REPORT_V1", "wdm-order-csv-v1", "WDM_ORDER_CLEANING_V1",
                "snapshot-1", "a".repeat(64), "9007199254740993", "2026-11-05T01:02:03.123456Z",
                "VALID", "READY", true,
                new ValidationReport.Counts("5", "5", "3", "3", "1", "2", "2", "1", "0", "1", "2", "2"),
                List.of(new ValidationReport.Diagnostic("BUSINESS", "PRODUCT_NOT_FOUND", "product_id", "RECORD_RANGE", List.of(first)),
                        new ValidationReport.Diagnostic("CONSISTENCY", "DUPLICATE_LINE_ID", "order_item_id", "RECORD_RANGE", List.of(first, second))),
                List.of(new ValidationReport.Decision(first, "REJECTED_ORDER", null, List.of("BUSINESS_RULE_FAILURE"))));
    }

    @Test void freezesPublicWireShapeIncludingStringCountsPositionsAndManifestExclusion() {
        var actual = mapper.readTree(mapper.writeValueAsString(presenter.page(report(), 0, 1)));
        assertEquals(mapper.readTree("""
                {"snapshotId":"snapshot-1","reportVersion":"WDM_VALIDATION_REPORT_V1","contractVersion":"wdm-order-csv-v1",
                 "policyVersion":"WDM_ORDER_CLEANING_V1","rawSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                 "rawSizeBytes":"9007199254740993","completedAt":"2026-11-05T01:02:03.123456Z",
                 "validationStatus":"VALID","cleaningOutcome":"READY","countsComplete":true,
                 "counts":{"parsedRecords":"5","inputRecords":"5","inputOrders":"3","knownOrders":"3",
                 "businessInvalidRecords":"1","keptRecords":"2","rejectedRecords":"2","deduplicatedRecords":"1",
                 "blockedRecords":"0","keptOrders":"1","rejectedOrders":"2","issueCount":"2"},
                 "issues":{"content":[{"stage":"BUSINESS","code":"PRODUCT_NOT_FOUND","field":"product_id",
                 "positionAccuracy":"RECORD_RANGE","positions":[{"recordNumber":2,"startLine":2,"endLine":4}]}],
                 "page":0,"size":1,"totalElements":"2","totalPages":"2"}}
                """), actual);
        assertFalse(actual.has("manifest"));
    }

    @Test void paginationPreservesOrderAndSafelyHandlesBeyondEndWithoutChangingSource() {
        var source = report();
        var page = presenter.page(source, 1, 1).issues();
        assertEquals(List.of(new ValidationResponse.Diagnostic("CONSISTENCY", "DUPLICATE_LINE_ID", "order_item_id", "RECORD_RANGE",
                List.of(new ValidationResponse.Position(2, 2, 4), new ValidationResponse.Position(3, 5, 5)))), page.content());
        assertEquals("2", page.totalElements()); assertEquals("2", page.totalPages());
        var beyondEnd = presenter.page(source, Integer.MAX_VALUE, 100).issues();
        assertTrue(beyondEnd.content().isEmpty()); assertEquals("1", beyondEnd.totalPages());
        assertEquals(2, source.diagnostics().size()); assertEquals(1, source.manifest().size());
        var mutable = new ArrayList<>(page.content());
        var copy = new ValidationResponse.DiagnosticPage(mutable, 1, 1, "2", "2"); mutable.clear();
        assertEquals(1, copy.content().size());
        assertThrows(UnsupportedOperationException.class, () -> copy.content().clear());
    }

    @Test void incompleteStructuralReportRetainsNullCountersAndDecoderCursorAccuracy() {
        var source = report();
        var structural = new ValidationReport(source.reportVersion(), source.contractVersion(), source.policyVersion(),
                source.snapshotId(), source.rawSha256(), source.rawSizeBytes(), source.completedAt(), "INVALID",
                "NOT_RUN_STRUCTURAL_ERROR", false,
                new ValidationReport.Counts("1", null, null, null, null, null, null, null, null, null, null, "1"),
                List.of(new ValidationReport.Diagnostic("STRUCTURE", "CSV_INVALID_UTF8", null, "DECODER_CURSOR", List.of())), List.of());
        var actual = mapper.readTree(mapper.writeValueAsString(presenter.page(structural, 0, 20)));
        assertFalse(actual.get("countsComplete").asBoolean());
        assertEquals("INVALID", actual.get("validationStatus").asString());
        assertEquals(mapper.readTree("""
                {"parsedRecords":"1","inputRecords":null,"inputOrders":null,"knownOrders":null,"businessInvalidRecords":null,
                 "keptRecords":null,"rejectedRecords":null,"deduplicatedRecords":null,"blockedRecords":null,
                 "keptOrders":null,"rejectedOrders":null,"issueCount":"1"}
                """), actual.get("counts"));
        assertEquals(mapper.readTree("""
                {"content":[{"stage":"STRUCTURE","code":"CSV_INVALID_UTF8","field":null,"positionAccuracy":"DECODER_CURSOR",
                 "positions":[]}],"page":0,"size":20,"totalElements":"1","totalPages":"1"}
                """), actual.get("issues"));
    }
}
