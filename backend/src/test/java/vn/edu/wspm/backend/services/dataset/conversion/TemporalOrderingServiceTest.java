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
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class TemporalOrderingServiceTest {
    private static final String TIME = "2026-11-01T00:00:00.000000Z";
    private static final List<String> CODEPOINT_IDS = List.of(" o", "O", "e\u0301", "o", "o ", "o-10", "o-2", "o10", "é", "\uE000", "\uD800\uDC00", "😀");
    private final TemporalOrderingService service = new TemporalOrderingService();

    private static ParsedCsvRecord row(long number, String customer, String order, String time, String product) {
        return new ParsedCsvRecord(number, number + 1, number + 2, List.of(order, Long.toString(number), customer,
                time, "COMPLETED", product, "historical-sku", "Historical name", "2", "100.00", "0.00", "200.00", "VND"));
    }

    private static GroupedDataset.GroupedOrder order(String id, String time) {
        return new GroupedDataset.GroupedOrder(id, Instant.parse(time), "VND", List.of(1, Integer.MAX_VALUE),
                List.of(row(2, "c", id, time, "p")));
    }

    private static GroupedDataset.CustomerGroup customer(String code, GroupedDataset.GroupedOrder... orders) {
        var map = new LinkedHashMap<String, GroupedDataset.GroupedOrder>();
        for (var order : orders) map.put(order.orderId(), order);
        return new GroupedDataset.CustomerGroup(code, map);
    }

    private static GroupedDataset input(GroupedDataset.CustomerGroup... customers) {
        var map = new LinkedHashMap<String, GroupedDataset.CustomerGroup>();
        for (var customer : customers) map.put(customer.customerCode(), customer);
        return new GroupedDataset(map);
    }

    private static List<String> orderIds(OrderedDataset result) {
        return result.customers().getFirst().orders().stream().map(GroupedDataset.GroupedOrder::orderId).toList();
    }

    @Test void sortsAcrossYearsDaysSecondsAndMicrosecondsWithoutRounding() {
        var prior = order("prior", "2025-12-31T23:59:59.999999Z");
        var first = order("z", "2026-11-01T00:00:00.000001Z");
        var second = order("a", "2026-11-01T00:00:00.000002Z");
        var nextSecond = order("second", "2026-11-01T00:00:01.000000Z");
        var nextDay = order("day", "2026-11-02T00:00:00.000000Z");
        var nextYear = order("year", "2027-01-01T00:00:00.000000Z");
        var grouped = input(customer("c", nextYear, nextDay, nextSecond, second, first, prior));
        var result = service.order(grouped);
        assertEquals(List.of("prior", "z", "a", "second", "day", "year"), orderIds(result));
        assertSame(first, result.customers().getFirst().orders().get(1));
        assertSame(second, result.customers().getFirst().orders().get(2));
        assertEquals(1000, result.customers().getFirst().orders().get(1).orderedAt().getNano());
        assertEquals(2000, result.customers().getFirst().orders().get(2).orderedAt().getNano());
        assertEquals(List.of("year", "day", "second", "a", "z", "prior"),
                List.copyOf(grouped.customers().get("c").orders().keySet()));
    }

    @Test void equalTimestampUsesOpaqueStringPrefixOrderRatherThanNumericSuffix() {
        assertEquals(List.of("order", "order-1", "order-10", "order-2"), orderIds(service.order(input(customer("c",
                order("order-2", TIME), order("order-10", TIME), order("order", TIME), order("order-1", TIME))))));
    }

    @Test void equalTimestampUsesUnicodeCodePointsIncludingOutsideBmpWithoutNormalization() {
        var reverse = new ArrayList<>(CODEPOINT_IDS); Collections.reverse(reverse);
        var orders = reverse.stream().map(id -> order(id, TIME)).toArray(GroupedDataset.GroupedOrder[]::new);
        assertEquals(CODEPOINT_IDS, orderIds(service.order(input(customer("c", orders)))));
        // UTF-16 natural order would put U+10000 before U+E000; the contract requires the reverse.
        assertTrue("\uD800\uDC00".compareTo("\uE000") < 0);
    }

    @Test void customersUseTheSameCodePointOrderIndependentlyOfTheirOrderDates() {
        var reverse = new ArrayList<>(CODEPOINT_IDS); Collections.reverse(reverse);
        var customers = new ArrayList<GroupedDataset.CustomerGroup>();
        for (String code : reverse) customers.add(customer(code, order("unique-" + customers.size(), TIME)));
        assertEquals(CODEPOINT_IDS, service.order(input(customers.toArray(GroupedDataset.CustomerGroup[]::new)))
                .customers().stream().map(OrderedDataset.OrderedCustomer::customerCode).toList());
    }

    @Test void equalTimesAndItemsRemainSeparateWithinAndAcrossCustomers() {
        var a = order("a", TIME); var b = order("b", TIME); var c = order("c", TIME);
        var result = service.order(input(customer("z", c), customer("a", b, a)));
        assertEquals(List.of("a", "z"), result.customers().stream().map(OrderedDataset.OrderedCustomer::customerCode).toList());
        assertEquals(List.of(a, b), result.customers().getFirst().orders());
        assertEquals(List.of(c), result.customers().getLast().orders());
        assertEquals(3, result.customers().stream().mapToInt(customer -> customer.orders().size()).sum());
    }

    @Test void retainedRecordsAndOrderMetadataAreNeitherSortedNorReconstructed() {
        var lateLine = row(9, "c", "o", TIME, "p"); var earlyLine = row(2, "c", "o", TIME, "q");
        var original = new GroupedDataset.GroupedOrder("o", Instant.parse(TIME), "USD", List.of(12, 27), List.of(lateLine, earlyLine));
        var output = service.order(input(customer("c", original))).customers().getFirst().orders().getFirst();
        assertSame(original, output);
        assertSame(original.itemIds(), output.itemIds());
        assertSame(original.sourceRecords(), output.sourceRecords());
        assertEquals(List.of(lateLine, earlyLine), output.sourceRecords());
        assertEquals("USD", output.currencyCode());
    }

    @Test void shuffledRawRowsThroughGroupingProduceIdenticalCustomerOrderItemStructure() {
        var rows = new ArrayList<>(List.of(row(2, "c2", "o3", TIME, "p"), row(3, "c1", "o2", TIME, "q"),
                row(4, "c1", "o1", TIME, "p"), row(5, "c1", "o1", TIME, "r"),
                row(6, "c1", "earlier", "2026-10-31T23:59:59.999999Z", "q")));
        var dictionary = new ProductItemDictionary(Map.of("p", 27, "q", 12, "r", 99));
        var expected = List.of("c1/earlier/[12]", "c1/o1/[27, 99]", "c1/o2/[12]", "c2/o3/[27]");
        var random = new Random(303);
        for (int attempt = 0; attempt < 8; attempt++) {
            Collections.shuffle(rows, random);
            var grouped = new CustomerGroupingService().group(new VerifiedConversionInput(new byte[0], null, rows), dictionary);
            var result = service.order(grouped);
            assertEquals(expected, result.customers().stream().flatMap(c -> c.orders().stream()
                    .map(o -> c.customerCode() + "/" + o.orderId() + "/" + o.itemIds())).toList());
            result.customers().forEach(c -> c.orders().forEach(o -> assertSame(grouped.customers().get(c.customerCode())
                    .orders().get(o.orderId()), o)));
            var readback = result.customers().stream().flatMap(c -> c.orders().stream()).flatMap(o -> o.sourceRecords().stream()).toList();
            assertEquals(rows.size(), readback.size());
            assertEquals(java.util.Set.copyOf(rows), java.util.Set.copyOf(readback));
        }
    }

    @Test void resultsAndConstructorsAreDefensiveImmutableAndRedactToString() {
        var original = order("private-order", TIME);
        var result = service.order(input(customer("private-customer", original)));
        assertThrows(UnsupportedOperationException.class, () -> result.customers().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.customers().getFirst().orders().clear());
        var orders = new ArrayList<>(List.of(original));
        var customer = new OrderedDataset.OrderedCustomer("private-customer", orders);
        var customers = new ArrayList<>(List.of(customer));
        var copy = new OrderedDataset(customers);
        orders.clear(); customers.clear();
        assertEquals(List.of(original), copy.customers().getFirst().orders());
        assertFalse(copy.toString().contains("private"));
        assertFalse(customer.toString().contains("private"));
    }

    @Test void emptyGroupedUtilityInputStaysEmptyAndNullIsCallerMisuse() {
        assertTrue(service.order(new GroupedDataset(Map.of())).customers().isEmpty());
        assertThrows(NullPointerException.class, () -> service.order(null));
    }

    @Test void customerAndOrderMapKeysMustMatchTheirMetadata() {
        rejected(new GroupedDataset(Map.of("wrong", customer("c", order("o", TIME)))));
        rejected(input(new GroupedDataset.CustomerGroup("c", Map.of("wrong", order("o", TIME)))));
        rejected(input(customer(" ", order("o", TIME))));
        rejected(input(customer("c", order(" ", TIME))));
    }

    @Test void emptyGroupsOrdersAndRepeatedGlobalOrderIdentityAreRejected() {
        rejected(input(customer("c")));
        var original = order("o", TIME);
        rejected(input(customer("c", new GroupedDataset.GroupedOrder("o", original.orderedAt(), "VND", List.of(), original.sourceRecords()))));
        rejected(input(customer("c", new GroupedDataset.GroupedOrder("o", original.orderedAt(), "VND", List.of(1), List.of()))));
        rejected(input(customer("c1", original), customer("c2", original)));
    }

    @Test void invalidItemsetShapeFailsInsteadOfRepairingGrouping() {
        var original = order("o", TIME);
        for (var items : List.of(List.of(0), List.of(-1), List.of(2, 1), List.of(1, 1))) {
            rejected(input(customer("c", new GroupedDataset.GroupedOrder("o", original.orderedAt(), "VND", items, original.sourceRecords()))));
        }
    }

    private void rejected(GroupedDataset input) {
        var failure = assertThrows(ApplicationException.class, () -> service.order(input));
        assertEquals(ErrorCode.INVALID_RESOURCE_STATE, failure.errorCode());
        assertEquals("Grouped ordering input is inconsistent.", failure.getMessage());
    }
}
