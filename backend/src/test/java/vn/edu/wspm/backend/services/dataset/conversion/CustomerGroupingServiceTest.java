package vn.edu.wspm.backend.services.dataset.conversion;

import org.junit.jupiter.api.Test;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.services.dataset.csv.ParsedCsvRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class CustomerGroupingServiceTest {
    private static final String EARLY = "2026-11-01T00:00:00.000001Z";
    private static final String LATE = "2026-11-02T00:00:00.999999Z";
    private final CustomerGroupingService service = new CustomerGroupingService();
    private final ProductItemDictionary mapping = new ProductItemDictionary(Map.of("p", 27, "q", 12, "r", 99));

    private static ParsedCsvRecord row(long number, String order, String customer, String product, String time) {
        return new ParsedCsvRecord(number, number + 1, number + 2, List.of(order, Long.toString(number), customer,
                time, "COMPLETED", product, "old-sku", "Historical name", "2", "100.00", "1.00", "199.00", "VND"));
    }

    private static ParsedCsvRecord field(ParsedCsvRecord row, int index, String value) {
        var fields = new ArrayList<>(row.fields()); fields.set(index, value);
        return new ParsedCsvRecord(row.recordNumber(), row.startLine(), row.endLine(), fields);
    }

    private static VerifiedConversionInput proof(List<ParsedCsvRecord> records) {
        // Package-local, already-kept handoff fixtures. Actual admission is exercised separately on MySQL.
        return new VerifiedConversionInput(new byte[]{1, 2}, null, records);
    }

    private GroupedDataset group(ProductItemDictionary dictionary, ParsedCsvRecord... records) {
        return service.group(proof(List.of(records)), dictionary);
    }

    private static Map<String, Map<String, List<Integer>>> membership(GroupedDataset grouped) {
        var customers = new TreeMap<String, Map<String, List<Integer>>>();
        grouped.customers().forEach((customer, group) -> {
            var orders = new TreeMap<String, List<Integer>>();
            group.orders().forEach((order, contents) -> orders.put(order, contents.itemIds()));
            customers.put(customer, orders);
        });
        return customers;
    }

    @Test void groupsCustomersOrdersAndItemsWithoutLosingAnyRetainedLine() {
        var records = List.of(row(2, "o1", "c1", "p", EARLY), row(3, "o1", "c1", "q", EARLY),
                row(4, "o2", "c1", "p", LATE), row(5, "o3", "c2", "r", EARLY));
        var input = proof(records);
        var grouped = service.group(input, mapping);
        assertEquals(Map.of("c1", Map.of("o1", List.of(12, 27), "o2", List.of(27)),
                "c2", Map.of("o3", List.of(99))), membership(grouped));
        var readback = grouped.customers().values().stream().flatMap(c -> c.orders().values().stream())
                .flatMap(o -> o.sourceRecords().stream()).toList();
        assertEquals(records, readback);
        for (int i = 0; i < records.size(); i++) assertSame(records.get(i), readback.get(i));
        assertEquals(records, input.keptRecords());
        assertArrayEquals(new byte[]{1, 2}, input.rawBytes());
    }

    @Test void repeatedProductAndQuantityChangeOnlyItemsetMembershipNotSourcePayload() {
        var first = row(2, "o", "c", "p", EARLY);
        var second = field(field(field(row(3, "o", "c", "p", EARLY), 8, "4294967295"), 6, "other-old-sku"), 7, "Other historical name");
        var dictionary = new ProductItemDictionary(Map.of("p", Integer.MAX_VALUE));
        var order = group(dictionary, first, second).customers().get("c").orders().get("o");
        assertEquals(List.of(Integer.MAX_VALUE), order.itemIds());
        assertEquals(List.of(first, second), order.sourceRecords());
        assertEquals("2", order.sourceRecords().getFirst().fields().get(8));
        assertEquals("4294967295", order.sourceRecords().getLast().fields().get(8));
        assertEquals("Other historical name", order.sourceRecords().getLast().fields().get(7));
        assertEquals("199.00", order.sourceRecords().getLast().fields().get(11));
    }

    @Test void sameProductInDifferentOrdersAndCustomersRemainsInEachOrder() {
        var dictionary = new ProductItemDictionary(Map.of("p", 27));
        assertEquals(Map.of("c1", Map.of("o1", List.of(27), "o2", List.of(27)),
                        "c2", Map.of("o3", List.of(27))), membership(group(dictionary,
                row(2, "o1", "c1", "p", EARLY), row(3, "o2", "c1", "p", EARLY), row(4, "o3", "c2", "p", EARLY))));
    }

    @Test void equalTimestampsDoNotMergeOrdersAndEncounterOrderIsNotChronology() {
        var dictionary = new ProductItemDictionary(Map.of("p", 1));
        var orders = group(dictionary, row(2, "late", "c", "p", LATE), row(3, "early1", "c", "p", EARLY),
                row(4, "early2", "c", "p", EARLY)).customers().get("c").orders();
        assertEquals(List.of("late", "early1", "early2"), List.copyOf(orders.keySet()));
        assertEquals(Instant.parse(LATE), orders.get("late").orderedAt());
        assertEquals(Instant.parse(EARLY), orders.get("early1").orderedAt());
        assertEquals(Instant.parse(EARLY), orders.get("early2").orderedAt());
    }

    @Test void interleavingAndReversalPreserveMembershipAndAllSourcePositions() {
        var records = new ArrayList<>(List.of(row(2, "o1", "c1", "p", EARLY), row(3, "o2", "c2", "q", LATE),
                row(4, "o1", "c1", "r", EARLY), row(5, "o3", "c1", "q", EARLY)));
        var before = service.group(proof(records), mapping);
        Collections.reverse(records);
        var after = service.group(proof(records), mapping);
        assertEquals(membership(before), membership(after));
        var readback = after.customers().values().stream().flatMap(c -> c.orders().values().stream())
                .flatMap(o -> o.sourceRecords().stream()).toList();
        assertEquals(4, readback.size());
        assertEquals(Set.copyOf(records), Set.copyOf(readback));
    }

    @Test void customerIdentityIsExactWithinOneSnapshot() {
        var codes = List.of("C", "c", " C", "c ", "é", "e\u0301");
        var rows = new ArrayList<ParsedCsvRecord>();
        for (String code : codes) rows.add(row(rows.size() + 2L, "o" + rows.size(), code, "p", EARLY));
        var result = service.group(proof(rows), new ProductItemDictionary(Map.of("p", 1)));
        assertEquals(Set.copyOf(codes), result.customers().keySet());
        result.customers().forEach((key, group) -> assertEquals(key, group.customerCode()));
    }

    @Test void orderAndProductIdentityAreExactAndNeverBasedOnSharedSkuOrName() {
        var ids = List.of("O", "o", " o", "o ", "é", "e\u0301");
        var rows = new ArrayList<ParsedCsvRecord>();
        var dictionary = new LinkedHashMap<String, Integer>();
        for (String id : ids) {
            dictionary.put(id, rows.size() + 1);
            rows.add(row(rows.size() + 2L, id, "c", id, EARLY));
        }
        var result = service.group(proof(rows), new ProductItemDictionary(dictionary));
        assertEquals(Set.copyOf(ids), result.customers().get("c").orders().keySet());
        result.customers().get("c").orders().forEach((id, order) -> assertEquals(List.of(dictionary.get(id)), order.itemIds()));
    }

    @Test void singleOrderCannotCrossCustomerTimeOrCurrencyBoundaries() {
        var first = row(2, "o", "c", "p", EARLY);
        var second = row(3, "o", "c", "p", EARLY);
        for (var conflict : List.of(field(second, 2, "another"), field(second, 3, LATE), field(second, 12, "USD"))) {
            rejected(() -> group(new ProductItemDictionary(Map.of("p", 1)), first, conflict));
        }
    }

    @Test void identityLengthUsesUnicodeCodePointsAndKeepsSupplementaryCharacters() {
        String customer = "😀".repeat(80), id = "😀".repeat(40);
        var result = group(new ProductItemDictionary(Map.of(id, 1)), row(2, id, customer, id, EARLY));
        assertEquals(List.of(1), result.customers().get(customer).orders().get(id).itemIds());
        rejected(() -> group(new ProductItemDictionary(Map.of(id, 1)), row(2, id, customer + "😀", id, EARLY)));
    }

    @Test void separateOrdersCanHaveDifferentCurrenciesWithoutSplittingCustomer() {
        var result = group(new ProductItemDictionary(Map.of("p", 1)), row(2, "a", "c", "p", EARLY),
                field(row(3, "b", "c", "p", EARLY), 12, "USD"));
        assertEquals(1, result.customers().size());
        assertEquals("USD", result.customers().get("c").orders().get("b").currencyCode());
    }

    @Test void dictionaryMustMatchTheEntireKeptProductSetExactly() {
        var row = row(2, "o", "c", "p", EARLY);
        rejected(() -> group(new ProductItemDictionary(Map.of()), row));
        rejected(() -> group(new ProductItemDictionary(Map.of("q", 1)), row));
        rejected(() -> group(new ProductItemDictionary(Map.of("p", 1, "q", 2)), row));
    }

    @Test void duplicateKeptSourceOrLineIdentityFailsInsteadOfCountingTwice() {
        var first = row(2, "o", "c", "p", EARLY);
        var dictionary = new ProductItemDictionary(Map.of("p", 1));
        rejected(() -> group(dictionary, first, first));
        rejected(() -> group(dictionary, first, field(row(3, "o2", "c", "p", EARLY), 1, "2")));
    }

    @Test void malformedInternalRowsOrTimestampsFailSafely() {
        var dictionary = new ProductItemDictionary(Map.of("p", 1));
        var valid = row(2, "o", "c", "p", EARLY);
        rejected(() -> group(dictionary, new ParsedCsvRecord(2, 2, 2, List.of("bad"))));
        rejected(() -> group(dictionary, new ParsedCsvRecord(1, 2, 2, valid.fields())));
        for (String timestamp : List.of("invalid", "2026-02-30T00:00:00.000000Z", "2026-11-01T00:00:60.000000Z",
                "2026-11-01T24:00:00.000000Z", "2026-11-01T00:00:00.000000+00:00")) {
            rejected(() -> group(dictionary, field(valid, 3, timestamp)));
        }
        for (int column : List.of(0, 1, 2, 5)) rejected(() -> group(dictionary, field(valid, column, " ")));
        rejected(() -> group(dictionary, field(valid, 2, "c".repeat(81))));
        rejected(() -> group(dictionary, field(valid, 0, "o".repeat(41))));
        rejected(() -> group(dictionary, field(valid, 5, "p".repeat(41))));
    }

    @Test void emptyInternalInputHasEmptyResultOnlyWithEmptyDictionary() {
        assertTrue(service.group(proof(List.of()), new ProductItemDictionary(Map.of())).customers().isEmpty());
        rejected(() -> service.group(proof(List.of()), mapping));
        assertThrows(NullPointerException.class, () -> service.group(null, mapping));
        assertThrows(NullPointerException.class, () -> service.group(proof(List.of()), null));
    }

    @Test void nestedCollectionsAreDefensiveAndUnmodifiableAndToStringIsRedacted() {
        var row = row(2, "private-order", "private-customer", "p", EARLY);
        var itemIds = new ArrayList<>(List.of(1));
        var records = new ArrayList<>(List.of(row));
        var order = new GroupedDataset.GroupedOrder("private-order", Instant.parse(EARLY), "VND", itemIds, records);
        var orders = new LinkedHashMap<>(Map.of("private-order", order));
        var customer = new GroupedDataset.CustomerGroup("private-customer", orders);
        var customers = new LinkedHashMap<>(Map.of("private-customer", customer));
        var result = new GroupedDataset(customers);
        itemIds.clear(); records.clear(); orders.clear(); customers.clear();
        assertEquals(List.of(1), order.itemIds());
        assertEquals(List.of(row), order.sourceRecords());
        assertEquals(1, customer.orders().size());
        assertEquals(1, result.customers().size());
        assertThrows(UnsupportedOperationException.class, () -> order.itemIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> order.sourceRecords().clear());
        assertThrows(UnsupportedOperationException.class, () -> customer.orders().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.customers().clear());
        assertThrows(UnsupportedOperationException.class, () -> row.fields().clear());
        for (Object value : List.of(order, customer, result)) {
            assertFalse(value.toString().contains("private"));
            assertFalse(value.toString().contains("Historical"));
        }
    }

    private static void rejected(org.junit.jupiter.api.function.Executable action) {
        var failure = assertThrows(ApplicationException.class, action);
        assertEquals(ErrorCode.INVALID_RESOURCE_STATE, failure.errorCode());
        assertEquals("Verified grouping input is inconsistent.", failure.getMessage());
    }
}
