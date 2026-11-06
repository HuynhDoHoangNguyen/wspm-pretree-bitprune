package vn.edu.wspm.backend.services.dataset.csv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.wspm.backend.services.dataset.csv.BusinessValidationIssue.Code.*;
import static vn.edu.wspm.backend.services.dataset.csv.OrderConsistencyIssue.Code.*;

class OrderConsistencyCheckerTest {
    private final OrderConsistencyChecker checker = new OrderConsistencyChecker();
    private final OrderCsvBusinessValidator validator = new OrderCsvBusinessValidator();
    private final Set<String> catalog = Set.of("product-laptop", "product-mouse", "product-bag");

    private ParsedCsvRecord row(long number, String order, String line) {
        return new ParsedCsvRecord(number, number, number, List.of(order, line, "anon-A",
                "2026-11-01T00:00:00.000000Z", "COMPLETED", "product-laptop", "SKU", "Name",
                "2", "100.00", "0.00", "200.00", "VND"));
    }

    private ParsedCsvRecord change(ParsedCsvRecord source, int column, String value) {
        var fields = new ArrayList<>(source.fields());
        fields.set(column, value);
        return new ParsedCsvRecord(source.recordNumber(), source.startLine(), source.endLine(), fields);
    }

    private OrderConsistencyInput input(ParsedCsvRecord source) {
        return new OrderConsistencyInput(source, validator.validate(source, catalog));
    }

    private OrderConsistencyChecker.Result check(ParsedCsvRecord... records) {
        return checker.check(java.util.Arrays.stream(records).map(this::input).toList());
    }

    private List<OrderConsistencyIssue> issues(OrderConsistencyChecker.Result result, OrderConsistencyIssue.Code code) {
        return result.issues().stream().filter(issue -> issue.code() == code).toList();
    }

    private List<Long> numbers(OrderConsistencyIssue issue) {
        return issue.positions().stream().map(OrderConsistencyIssue.Position::recordNumber).toList();
    }

    @Test
    void emptySingleAndMultipleLinesIncludingSameProductAreConsistent() {
        assertFalse(check().hasConsistencyIssues());
        assertFalse(check(row(2, "order", "1")).hasConsistencyIssues());
        var result = check(row(2, "order", "1"), row(3, "order", "2"), row(4, "other", "3"));
        assertFalse(result.hasConsistencyIssues());
        assertEquals(3, result.rows().size());
        assertTrue(result.affectedOrderIds().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"2,anon-B,customer_code", "3,2026-11-02T00:00:00.000000Z,ordered_at", "12,USD,currency_code"})
    void reportsOrderCommonFieldConflictAcrossNonAdjacentRows(int column, String value, String field) {
        var result = check(row(2, "order", "1"), row(3, "unrelated", "2"),
                change(row(4, "order", "3"), column, value));
        assertEquals(1, result.issues().size());
        var issue = result.issues().getFirst();
        assertEquals(ORDER_FIELD_CONFLICT, issue.code());
        assertEquals(field, issue.field());
        assertEquals(List.of(2L, 4L), numbers(issue));
        assertEquals(Set.of("order"), result.affectedOrderIds());
    }

    @Test
    void reportsAllCommonConflictsAndAllContributingRowsOncePerField() {
        var third = change(change(change(row(4, "order", "3"), 2, "anon-B"), 3,
                "2026-11-02T00:00:00.000000Z"), 12, "USD");
        var result = check(row(2, "order", "1"), row(3, "order", "2"), third);
        assertEquals(List.of("customer_code", "ordered_at", "currency_code"),
                result.issues().stream().map(OrderConsistencyIssue::field).toList());
        for (var issue : result.issues()) assertEquals(List.of(2L, 3L, 4L), numbers(issue));
    }

    @Test
    void exactRepeatedLineIsReportedWithoutCleaningOrDiscardingRows() {
        var result = check(row(2, "order", "1"), row(3, "order", "1"), row(4, "order", "1"));
        assertEquals(3, result.rows().size());
        assertEquals(1, result.issues().size());
        assertEquals(DUPLICATE_LINE_ID, result.issues().getFirst().code());
        assertEquals(List.of(2L, 3L, 4L), numbers(result.issues().getFirst()));
        assertEquals(Set.of("order"), result.affectedOrderIds());
    }

    @ParameterizedTest
    @CsvSource({"2,anon-B,customer_code", "3,2026-11-02T00:00:00.000000Z,ordered_at",
            "5,product-mouse,product_id", "6,OLD-SKU,sku_snapshot", "7,Historical name,product_name_snapshot",
            "12,USD,currency_code"})
    void repeatedLineWithDifferentValidContentReportsDuplicateAndFieldConflict(int column, String value, String field) {
        var result = check(row(2, "order", "1"), change(row(3, "order", "1"), column, value));
        assertEquals(1, issues(result, DUPLICATE_LINE_ID).size());
        assertEquals(1, issues(result, LINE_ID_CONTENT_CONFLICT).size());
        assertEquals(field, issues(result, LINE_ID_CONTENT_CONFLICT).getFirst().field());
    }

    @Test
    void detectsQuantityPriceDiscountAndTotalDifferencesWithoutChangingAmounts() {
        var second = change(change(change(change(row(3, "order", "1"), 8, "3"), 9, "200.00"),
                10, "10.00"), 11, "590.00");
        var result = check(row(2, "order", "1"), second);
        assertTrue(result.rows().stream().allMatch(row -> row.business().valid()));
        assertEquals(List.of("quantity", "list_unit_price", "allocated_discount_amount", "line_total"),
                issues(result, LINE_ID_CONTENT_CONFLICT).stream().map(OrderConsistencyIssue::field).toList());
        assertEquals("590.00", result.rows().get(1).source().fields().get(11));
    }

    @Test
    void globalLineIdentityMarksEveryRelatedOrderWithoutPairwiseDiagnosticExplosion() {
        var result = check(row(2, "z-order", "1"), row(3, "unrelated", "2"),
                row(4, "a-order", "1"), row(5, "m-order", "1"));
        assertEquals(List.of(DUPLICATE_LINE_ID, LINE_ID_ORDER_CONFLICT),
                result.issues().stream().map(OrderConsistencyIssue::code).toList());
        assertEquals(List.of("a-order", "m-order", "z-order"), new ArrayList<>(result.affectedOrderIds()));
        for (var issue : result.issues()) assertEquals(List.of(2L, 4L, 5L), numbers(issue));
        assertEquals("order_id", issues(result, LINE_ID_ORDER_CONFLICT).getFirst().field());
    }

    @Test
    void sameTimestampAcrossDifferentOrdersDoesNotCauseMergeOrConflict() {
        assertFalse(check(row(2, "order-a", "1"), row(3, "order-b", "2")).hasConsistencyIssues());
    }

    @Test
    void largeUnsignedLineIdsAreComparedWithoutNumericPrecisionLoss() {
        var first = row(2, "order-a", "9007199254740992");
        var second = row(3, "order-b", "9007199254740993");
        assertFalse(check(first, second).hasConsistencyIssues());
        var result = check(first, second, row(4, "order-c", "9007199254740993"),
                row(5, "order-d", "18446744073709551615"));
        assertEquals(List.of(3L, 4L), numbers(issues(result, DUPLICATE_LINE_ID).getFirst()));
        assertEquals(Set.of("order-b", "order-c"), result.affectedOrderIds());
    }

    @Test
    void orderIdentityIsExactWithoutCaseFoldingTrimmingOrUnicodeNormalization() {
        var rows = List.of("order", "ORDER", "order ", "é", "e\u0301");
        var inputs = new ArrayList<OrderConsistencyInput>();
        for (int index = 0; index < rows.size(); index++) {
            inputs.add(input(change(row(index + 2, rows.get(index), "" + (index + 1)), 2, "customer-" + index)));
        }
        assertFalse(checker.check(inputs).hasConsistencyIssues());
    }

    @Test
    void businessInvalidProductStillParticipatesInCrossOrderIdentityChecks() {
        var bad = change(row(3, "order-b", "1"), 5, "missing");
        var result = check(row(2, "order-a", "1"), bad);
        assertEquals(PRODUCT_NOT_FOUND, result.rows().get(1).business().issues().getFirst().code());
        assertEquals(1, issues(result, DUPLICATE_LINE_ID).size());
        assertEquals(1, issues(result, LINE_ID_ORDER_CONFLICT).size());
        assertTrue(issues(result, LINE_ID_CONTENT_CONFLICT).isEmpty()); // bad product is not trusted evidence
        assertEquals(Set.of("order-a", "order-b"), result.affectedOrderIds());
    }

    @Test
    void invalidMonetaryRowStillContributesValidOrderMetadata() {
        var bad = change(change(row(3, "order", "2"), 2, "anon-B"), 9, "NaN");
        var result = check(row(2, "order", "1"), bad);
        assertEquals(INVALID_MONEY_FORMAT, result.rows().get(1).business().issues().getFirst().code());
        assertEquals(1, issues(result, ORDER_FIELD_CONFLICT).size());
        assertEquals("customer_code", result.issues().getFirst().field());
    }

    @Test
    void invalidOrderKeyDoesNotHideUsableDuplicateLineOrInventAffectedOrder() {
        var result = check(row(2, "good-order", "1"), change(row(3, "ignored", "1"), 0, " "));
        assertEquals(1, issues(result, DUPLICATE_LINE_ID).size());
        assertTrue(issues(result, LINE_ID_ORDER_CONFLICT).isEmpty());
        assertEquals(Set.of("good-order"), result.affectedOrderIds());
        assertEquals(FIELD_REQUIRED, result.rows().get(1).business().issues().getFirst().code());
    }

    @Test
    void invalidLineKeyDoesNotHideOrderConflictOrInventDuplicate() {
        var bad = change(change(row(3, "order", "1"), 1, "01"), 2, "anon-B");
        var result = check(row(2, "order", "1"), bad);
        assertEquals(1, issues(result, ORDER_FIELD_CONFLICT).size());
        assertTrue(issues(result, DUPLICATE_LINE_ID).isEmpty());
        assertEquals(2, result.rows().size());
    }

    @ParameterizedTest
    @CsvSource({"2,' ',customer_code", "3,not-a-time,ordered_at", "12,vnd,currency_code"})
    void invalidCommonFieldsDoNotBecomeFalseConflicts(int column, String value, String field) {
        var result = check(row(2, "order", "1"), change(row(3, "order", "2"), column, value));
        assertFalse(result.hasConsistencyIssues());
        assertEquals(field, result.rows().get(1).business().issues().getFirst().field());
    }

    @Test
    void statusFailureAndTotalMismatchStayBusinessErrorsNotContentConflicts() {
        var bad = change(change(row(3, "order", "1"), 4, "CANCELLED"), 11, "199.00");
        var result = check(row(2, "order", "1"), bad);
        assertEquals(List.of(STATUS_NOT_COMPLETED, LINE_TOTAL_MISMATCH), result.rows().get(1).business()
                .issues().stream().map(BusinessValidationIssue::code).toList());
        assertEquals(List.of(DUPLICATE_LINE_ID), result.issues().stream().map(OrderConsistencyIssue::code).toList());
    }

    @Test
    void rowsWithNoUsableKeysAreRetainedAndNeverMisreportedAsValidDataset() {
        var bad = change(change(row(2, "order", "1"), 0, ""), 1, "0");
        var result = check(bad);
        assertFalse(result.hasConsistencyIssues());
        assertFalse(result.rows().getFirst().business().valid());
        assertEquals(2, result.rows().getFirst().business().issues().size());
        assertTrue(result.affectedOrderIds().isEmpty());
    }

    @Test
    void onlyComparableRowsAreListedAsConflictEvidence() {
        var result = check(row(2, "order", "1"), change(row(3, "order", "2"), 2, " "),
                change(row(4, "order", "3"), 2, "anon-B"));
        assertEquals(List.of(2L, 4L), numbers(result.issues().getFirst()));
        assertEquals(3, result.rows().size());
    }

    @Test
    void resultIsStableAcrossInputCollectionOrderImmutableAndSafeToLog() {
        var inputs = new ArrayList<>(List.of(input(row(2, "private-order", "1")),
                input(change(row(3, "private-order", "1"), 2, "private-customer"))));
        var original = List.copyOf(inputs);
        var result = checker.check(inputs);
        Collections.reverse(inputs);
        assertEquals(result, checker.check(inputs));
        assertEquals(original, result.rows());
        assertFalse(result.toString().contains("private-"));
        assertFalse(result.rows().toString().contains("private-"));
        assertFalse(result.issues().toString().contains("private-"));
        assertThrows(UnsupportedOperationException.class, () -> result.rows().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.issues().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.affectedOrderIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.issues().getFirst().positions().clear());
        inputs.clear();
        assertEquals(2, result.rows().size());
    }

    @Test
    void sourceMultilinePositionsAreNotReconstructedFromRecordNumber() {
        var source = row(2, "order", "1");
        var first = new ParsedCsvRecord(2, 2, 4, source.fields());
        var second = new ParsedCsvRecord(3, 5, 5, source.fields());
        assertEquals(List.of(new OrderConsistencyIssue.Position(2, 2, 4), new OrderConsistencyIssue.Position(3, 5, 5)),
                check(first, second).issues().getFirst().positions());
    }

    @Test
    void rejectsMismatchedPositionsAndDuplicateRecordNumbersAsCallerMisuse() {
        var source = row(2, "order", "1");
        var other = row(3, "order", "2");
        assertThrows(IllegalArgumentException.class, () -> new OrderConsistencyInput(source, validator.validate(other, catalog)));
        var invalid = change(other, 5, "missing");
        assertThrows(IllegalArgumentException.class, () -> new OrderConsistencyInput(source, validator.validate(invalid, catalog)));
        assertThrows(IllegalArgumentException.class, () -> check(source, source));
        assertThrows(IllegalArgumentException.class, () -> new OrderConsistencyInput(
                new ParsedCsvRecord(1, 1, 1, source.fields()), validator.validate(source, catalog)));
    }

    @Test
    void fixturesComposeAllThreeStagesWithExactExpectedFindingsAndNoDroppedRows() throws Exception {
        var valid = fixture("valid.csv");
        assertEquals(5, valid.rows().size());
        assertFalse(valid.hasConsistencyIssues());
        assertEquals(2, valid.rows().getFirst().source().startLine());
        assertEquals(3, valid.rows().getFirst().source().endLine());
        assertTrue(fixture("empty.csv").rows().isEmpty());
        var bad = fixture("business-invalid.csv");
        assertEquals(5, bad.rows().size());
        assertEquals(PRODUCT_NOT_FOUND, bad.rows().getFirst().business().issues().getFirst().code());
        assertEquals(List.of(ORDER_FIELD_CONFLICT, DUPLICATE_LINE_ID),
                bad.issues().stream().map(OrderConsistencyIssue::code).toList());
        assertEquals(List.of(3L, 4L), numbers(bad.issues().getFirst()));
        assertEquals(List.of(5L, 6L), numbers(bad.issues().get(1)));
        assertEquals(Set.of("order-conflict", "order-duplicate"), bad.affectedOrderIds());
    }

    private OrderConsistencyChecker.Result fixture(String name) throws Exception {
        var inputs = new ArrayList<OrderConsistencyInput>();
        try (var source = getClass().getResourceAsStream("/wdm/csv-v1/" + name)) {
            assertNotNull(source);
            new OrderCsvParser(16_384).parse(source, row -> inputs.add(input(row)));
        }
        return checker.check(inputs);
    }
}
