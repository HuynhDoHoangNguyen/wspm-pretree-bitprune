package vn.edu.wspm.backend.services.dataset.csv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.wspm.backend.services.dataset.csv.OrderCleaningResult.Action.*;
import static vn.edu.wspm.backend.services.dataset.csv.OrderCleaningResult.Outcome.*;
import static vn.edu.wspm.backend.services.dataset.csv.OrderCleaningResult.Reason.*;

class OrderCleaningPipelineTest {
    private final OrderCleaningPipeline pipeline = new OrderCleaningPipeline();
    private final OrderConsistencyChecker checker = new OrderConsistencyChecker();
    private final OrderCsvBusinessValidator validator = new OrderCsvBusinessValidator();
    private final Set<String> catalog = Set.of("product-laptop", "product-mouse", "product-bag");

    private ParsedCsvRecord row(long number, String order, String line) {
        return new ParsedCsvRecord(number, number, number, List.of(order, line, "anon-A",
                "2026-11-01T00:00:00.000000Z", "COMPLETED", "product-laptop", "OLD-SKU",
                "Historical name", "2", "100.00", "0.00", "200.00", "VND"));
    }

    private ParsedCsvRecord change(ParsedCsvRecord source, int column, String value) {
        var fields = new ArrayList<>(source.fields());
        fields.set(column, value);
        return new ParsedCsvRecord(source.recordNumber(), source.startLine(), source.endLine(), fields);
    }

    private OrderConsistencyChecker.Result checked(List<ParsedCsvRecord> rows) {
        return checker.check(rows.stream().map(source -> new OrderConsistencyInput(
                source, validator.validate(source, catalog))).toList());
    }

    private OrderCleaningResult clean(ParsedCsvRecord... rows) {
        var result = pipeline.clean(checked(List.of(rows)));
        assertAccounting(result);
        return result;
    }

    private void assertAccounting(OrderCleaningResult result) {
        assertEquals(OrderCleaningPipeline.POLICY_VERSION, result.policyVersion());
        assertEquals(result.source().rows().size(), result.dispositions().size());
        assertEquals(result.source().rows().size(), result.count(KEPT) + result.count(REJECTED_ORDER)
                + result.count(DEDUPLICATED) + result.count(BLOCKED));
        assertEquals(result.source().rows().stream().map(row -> row.source().recordNumber()).toList(),
                result.dispositions().stream().map(row -> row.position().recordNumber()).toList());
        assertEquals(result.count(KEPT), result.orders().stream().mapToLong(order -> order.lines().size()).sum());
        for (var disposition : result.dispositions()) {
            if (disposition.action() == KEPT || disposition.action() == DEDUPLICATED) {
                assertNotNull(disposition.representative());
                assertTrue(result.dispositions().stream().anyMatch(other -> other.action() == KEPT
                        && other.position().equals(disposition.representative())));
            } else assertNull(disposition.representative());
        }
    }

    @Test
    void emptyInputIsDistinctFromNoSurvivingOrders() {
        var empty = clean();
        assertEquals(EMPTY_INPUT, empty.outcome());
        assertFalse(empty.hasUsableOrders());
        var rejected = clean(change(row(2, "order", "1"), 5, "missing"));
        assertEquals(EMPTY_AFTER_CLEANING, rejected.outcome());
        assertFalse(rejected.hasUsableOrders());
        assertEquals(1, rejected.rejectedOrders().size());
        assertEquals(1, rejected.count(REJECTED_ORDER));
    }

    @Test
    void validOrdersRetainAllHistoricalValuesWithoutChronologyOrMoneyTransformation() {
        var first = change(row(2, "late-order", "1"), 3, "2026-11-02T00:00:00.000000Z");
        var second = row(3, "early-order", "2");
        var result = clean(first, second);
        assertEquals(READY, result.outcome());
        assertTrue(result.hasUsableOrders());
        assertEquals(List.of("late-order", "early-order"), result.orders().stream().map(CleanedOrder::orderId).toList());
        assertEquals(validator.validate(first, catalog).row(), result.orders().getFirst().lines().getFirst().values());
        assertEquals(2, result.count(KEPT));
        assertEquals(0, result.count(DEDUPLICATED));
    }

    @ParameterizedTest
    @CsvSource({"1,0", "2,' '", "3,bad-time", "4,CANCELLED", "5,missing", "6,' '",
            "7,' '", "8,-1", "9,NaN", "10,201.00", "11,199.00", "12,vnd"})
    void anyBusinessFailureRejectsWholeOrderAndKeepsUnrelatedOrders(int column, String value) {
        var result = clean(row(2, "bad-order", "1"), row(3, "good-order", "2"),
                change(row(4, "bad-order", "3"), column, value));
        assertEquals(List.of("good-order"), result.orders().stream().map(CleanedOrder::orderId).toList());
        assertEquals(List.of(BUSINESS_RULE_FAILURE), result.rejectedOrders().getFirst().reasons());
        assertEquals(List.of(2L, 4L), result.rejectedOrders().getFirst().positions().stream()
                .map(OrderConsistencyIssue.Position::recordNumber).toList());
        assertEquals(2, result.count(REJECTED_ORDER));
        assertEquals(1, result.count(KEPT));
    }

    @ParameterizedTest
    @CsvSource({"2,anon-B", "3,2026-11-02T00:00:00.000000Z", "12,USD"})
    void orderMetadataConflictRejectsWholeOrder(int column, String value) {
        var result = clean(row(2, "order", "1"), change(row(3, "order", "2"), column, value));
        assertEquals(EMPTY_AFTER_CLEANING, result.outcome());
        assertEquals(List.of(ORDER_FIELD_CONFLICT), result.rejectedOrders().getFirst().reasons());
        assertEquals(2, result.count(REJECTED_ORDER));
    }

    @Test
    void identicalRepeatedLineKeepsEarliestSourceAndDoesNotDoubleQuantityOrMoney() {
        var result = clean(row(4, "order", "1"), row(2, "order", "1"), row(3, "order", "1"));
        assertEquals(READY, result.outcome());
        assertTrue(result.rejectedOrders().isEmpty()); // affectedOrderIds is not a rejection list
        assertEquals(Set.of("order"), result.source().affectedOrderIds());
        var line = result.orders().getFirst().lines().getFirst();
        assertEquals(2, line.values().recordNumber());
        assertEquals(2, line.values().quantity());
        assertEquals(new BigDecimal("200.00"), line.values().lineTotal());
        assertEquals(List.of(2L, 3L, 4L), line.origins().stream().map(OrderConsistencyIssue.Position::recordNumber).toList());
        assertEquals(1, result.count(KEPT));
        assertEquals(2, result.count(DEDUPLICATED));
        assertEquals(List.of(IDENTICAL_LINE_REPETITION), result.dispositions().get(1).reasons());
        assertEquals(result.dispositions().getFirst().position(), result.dispositions().get(2).representative());
    }

    @Test
    void sameProductDifferentLineIdsAreGroupedWithoutInventingAggregateHistoricalValues() {
        var changed = change(change(change(change(row(4, "order", "3"), 6, "DIFFERENT-SKU"),
                7, "Different historical name"), 9, "150.00"), 11, "300.00");
        var result = clean(row(2, "order", "1"), change(row(3, "order", "2"), 5, "product-mouse"), changed);
        var order = result.orders().getFirst();
        assertEquals(3, order.lines().size());
        assertEquals(List.of("product-laptop", "product-mouse"), order.products().stream()
                .map(CleanedOrder.ProductGroup::productId).toList());
        var group = order.products().getFirst();
        assertEquals(List.of(2L, 4L), group.lines().stream().map(line -> line.values().recordNumber()).toList());
        assertSame(order.lines().get(2), group.lines().get(1));
        assertEquals(new BigDecimal("150.00"), group.lines().get(1).values().listUnitPrice());
        assertEquals("DIFFERENT-SKU", group.lines().get(1).values().skuSnapshot());
        assertEquals(3, result.count(KEPT));
    }

    @Test
    void repeatedLineWithDifferentPayloadRejectsOrderBeforeAnyDeduplication() {
        var result = clean(row(2, "order", "1"), row(3, "order", "1"),
                change(row(4, "order", "1"), 7, "Changed name"));
        assertEquals(List.of(LINE_ID_CONTENT_CONFLICT), result.rejectedOrders().getFirst().reasons());
        assertEquals(3, result.count(REJECTED_ORDER));
        assertEquals(0, result.count(DEDUPLICATED));
        assertTrue(result.orders().isEmpty());
    }

    @Test
    void businessErrorOnRepeatedLineCannotBeLaunderedByKeepingTheGoodCopy() {
        var result = clean(row(2, "order", "1"), change(row(3, "order", "1"), 5, "missing"));
        assertEquals(2, result.count(REJECTED_ORDER));
        assertEquals(0, result.count(KEPT));
        assertEquals(0, result.count(DEDUPLICATED));
        assertEquals(BusinessValidationIssue.Code.PRODUCT_NOT_FOUND,
                result.source().rows().get(1).business().issues().getFirst().code());
    }

    @Test
    void crossOrderIdentityRejectsEveryLinkedOrderIncludingOtherLinesAndInvalidProductRows() {
        var result = clean(row(2, "order-a", "1"), row(3, "order-b", "2"),
                change(row(4, "order-b", "1"), 5, "missing"), row(5, "good-order", "3"),
                row(6, "order-c", "2"));
        assertEquals(List.of("good-order"), result.orders().stream().map(CleanedOrder::orderId).toList());
        assertEquals(List.of("order-a", "order-b", "order-c"), result.rejectedOrders().stream()
                .map(OrderCleaningResult.RejectedOrder::orderId).toList());
        assertEquals(4, result.count(REJECTED_ORDER));
        for (var order : result.rejectedOrders()) assertTrue(order.reasons().contains(LINE_ID_ORDER_CONFLICT));
        assertTrue(result.rejectedOrders().get(1).reasons().contains(BUSINESS_RULE_FAILURE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\u00a0"})
    void unassignableOrderBlocksEntireSnapshotWithoutPartialCleanOutput(String badKey) {
        var result = clean(row(2, "good-order", "1"), change(row(3, "ignored", "2"), 0, badKey),
                change(row(4, "known-bad-order", "3"), 5, "missing"));
        assertEquals(BLOCKED_UNATTRIBUTABLE_ORDER, result.outcome());
        assertFalse(result.hasUsableOrders());
        assertTrue(result.orders().isEmpty());
        assertTrue(result.rejectedOrders().isEmpty()); // processing stopped, not a partial order rejection report
        assertEquals(3, result.count(BLOCKED));
        assertEquals(List.of(SNAPSHOT_BLOCKED), result.dispositions().getFirst().reasons());
        assertEquals(List.of(UNATTRIBUTABLE_ORDER_ID), result.dispositions().get(1).reasons());
        assertFalse(result.source().rows().get(2).business().valid());
    }

    @Test
    void overlongOrderIdIsAlsoUnassignableAndCannotBeUsedAsRawGroupingKey() {
        assertEquals(BLOCKED_UNATTRIBUTABLE_ORDER, clean(row(2, "x".repeat(41), "1")).outcome());
    }

    @Test
    void allRejectionReasonsAreStableAndEveryRejectedRowHasWholeOrderReasonSet() {
        var first = row(2, "order-a", "1");
        var second = change(change(row(3, "order-a", "1"), 2, "anon-B"), 5, "missing");
        var third = row(4, "order-b", "1");
        var result = clean(first, second, third);
        assertEquals(List.of(BUSINESS_RULE_FAILURE, ORDER_FIELD_CONFLICT, LINE_ID_CONTENT_CONFLICT,
                LINE_ID_ORDER_CONFLICT), result.rejectedOrders().getFirst().reasons());
        assertEquals(result.dispositions().getFirst().reasons(), result.dispositions().get(1).reasons());
        assertEquals(3, result.count(REJECTED_ORDER));
    }

    @Test
    void rawOriginsAndSafeSummariesArePreservedAndAllNestedCollectionsAreImmutable() {
        var source = row(2, "private-order", "1");
        var first = new ParsedCsvRecord(2, 2, 4, source.fields());
        var second = new ParsedCsvRecord(3, 5, 5, source.fields());
        var checked = checked(List.of(first, second, change(row(4, "private-bad-order", "2"), 5, "missing")));
        var result = pipeline.clean(checked);
        assertAccounting(result);
        assertSame(checked, result.source());
        assertEquals(List.of(new OrderConsistencyIssue.Position(2, 2, 4), new OrderConsistencyIssue.Position(3, 5, 5)),
                result.orders().getFirst().lines().getFirst().origins());
        assertFalse(result.toString().contains("private-"));
        assertFalse(result.orders().toString().contains("private-"));
        assertFalse(result.rejectedOrders().toString().contains("private-"));
        assertFalse(result.orders().getFirst().products().toString().contains("product-laptop"));
        assertFalse(result.orders().getFirst().lines().toString().contains("Historical name"));
        assertThrows(UnsupportedOperationException.class, () -> result.orders().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.rejectedOrders().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.dispositions().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.orders().getFirst().lines().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.orders().getFirst().products().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.orders().getFirst().products().getFirst().lines().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.orders().getFirst().lines().getFirst().origins().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.rejectedOrders().getFirst().reasons().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.rejectedOrders().getFirst().positions().clear());
    }

    @Test
    void inputPermutationDoesNotChangeOutputAndSourceDataIsNotMutated() {
        var inputs = new ArrayList<>(List.of(row(2, "order-a", "1"), row(3, "order-b", "2"), row(4, "order-a", "1")));
        var sourceCopy = List.copyOf(inputs);
        var first = pipeline.clean(checked(inputs));
        Collections.reverse(inputs);
        assertEquals(first, pipeline.clean(checked(inputs)));
        assertEquals(sourceCopy, first.source().rows().stream().map(OrderConsistencyInput::source).toList());
    }

    @Test
    void recleaningOnlyRetainedRowsIntroducesNoFurtherCleaningAndPreservesCommercialData() {
        var initial = clean(row(2, "order", "1"), row(3, "order", "1"), row(4, "order", "2"));
        var kept = initial.dispositions().stream().filter(row -> row.action() == KEPT)
                .map(row -> initial.source().rows().stream().filter(input -> input.source().recordNumber()
                        == row.position().recordNumber()).findFirst().orElseThrow().source()).toList();
        var again = pipeline.clean(checked(kept));
        assertAccounting(again);
        assertEquals(0, again.count(DEDUPLICATED));
        assertEquals(0, again.count(REJECTED_ORDER));
        assertEquals(initial.orders().getFirst().lines().stream().map(CleanedOrder.Line::values).toList(),
                again.orders().getFirst().lines().stream().map(CleanedOrder.Line::values).toList());
    }

    @Test
    void incompleteOrStaleConsistencyFindingsAreRejectedRatherThanCleaningPartialEvidence() {
        var source = checked(List.of(row(2, "order-a", "1"), row(3, "order-b", "1")));
        var omitted = new OrderConsistencyChecker.Result(source.rows(), List.of(), source.affectedOrderIds());
        assertThrows(IllegalArgumentException.class, () -> pipeline.clean(omitted));
        var wrongLinks = new OrderConsistencyChecker.Result(source.rows(), source.issues(), Set.of());
        assertThrows(IllegalArgumentException.class, () -> pipeline.clean(wrongLinks));
    }

    @Test
    void fixturesComposeFourStagesAndEverySourceRowIsAccountedFor() throws Exception {
        var valid = fixture("valid.csv");
        assertEquals(READY, valid.outcome());
        assertEquals(3, valid.orders().size());
        assertEquals(5, valid.count(KEPT));
        assertEquals(2, valid.orders().getFirst().lines().size());
        assertEquals(1, valid.orders().getFirst().products().size());
        assertEquals(EMPTY_INPUT, fixture("empty.csv").outcome());
        var cleaned = fixture("business-invalid.csv");
        assertEquals(List.of("order-duplicate"), cleaned.orders().stream().map(CleanedOrder::orderId).toList());
        assertEquals(List.of("order-unknown", "order-conflict"), cleaned.rejectedOrders().stream()
                .map(OrderCleaningResult.RejectedOrder::orderId).toList());
        assertEquals(1, cleaned.count(KEPT));
        assertEquals(3, cleaned.count(REJECTED_ORDER));
        assertEquals(1, cleaned.count(DEDUPLICATED));
        assertEquals(List.of(5L, 6L), cleaned.orders().getFirst().lines().getFirst().origins().stream()
                .map(OrderConsistencyIssue.Position::recordNumber).toList());
        assertEquals(5, cleaned.source().rows().size());
    }

    private OrderCleaningResult fixture(String name) throws Exception {
        var inputs = new ArrayList<ParsedCsvRecord>();
        try (var source = getClass().getResourceAsStream("/wdm/csv-v1/" + name)) {
            assertNotNull(source);
            new OrderCsvParser(16_384).parse(source, inputs::add);
        }
        var result = pipeline.clean(checked(inputs));
        assertAccounting(result);
        return result;
    }
}
