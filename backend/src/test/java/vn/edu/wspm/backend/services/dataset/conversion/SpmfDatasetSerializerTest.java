package vn.edu.wspm.backend.services.dataset.conversion;

import org.junit.jupiter.api.Test;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.services.dataset.csv.ParsedCsvRecord;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SpmfDatasetSerializerTest {
    private static final String TIME = "2026-11-01T00:00:00.000000Z";
    private final SpmfDatasetSerializer serializer = new SpmfDatasetSerializer();

    private static GroupedDataset.GroupedOrder order(String id, Integer... items) {
        var source = new ParsedCsvRecord(2, 2, 2, List.of(id, "2", "private-customer", TIME, "COMPLETED",
                "private-product", "private-sku", "Tên lịch sử 😀", "2", "100.00", "0.00", "200.00", "VND"));
        return new GroupedDataset.GroupedOrder(id, Instant.parse(TIME), "VND", List.of(items), List.of(source));
    }

    private static OrderedDataset.OrderedCustomer customer(String id, GroupedDataset.GroupedOrder... orders) {
        return new OrderedDataset.OrderedCustomer(id, List.of(orders));
    }

    private static OrderedDataset input(OrderedDataset.OrderedCustomer... customers) {
        return new OrderedDataset(List.of(customers));
    }

    static SequenceDatabase parse(byte[] bytes) throws Exception {
        return new SpmfSequenceParser().parse(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
    }

    /** Compare every SID, sequence position, itemset boundary and exact item ID, not just totals. */
    static void assertExactReadback(OrderedDataset input, byte[] bytes) throws Exception {
        var parsed = parse(bytes);
        assertEquals(input.customers().size(), parsed.size());
        for (int customer = 0; customer < input.customers().size(); customer++) {
            var actual = parsed.records().get(customer);
            assertEquals(customer + 1, actual.sid());
            var expected = input.customers().get(customer).orders();
            assertEquals(expected.size(), actual.sequence().size());
            for (int itemset = 0; itemset < expected.size(); itemset++) {
                assertEquals(expected.get(itemset).itemIds(), actual.sequence().itemsets().get(itemset)
                        .items().stream().map(Item::id).toList());
            }
        }
    }

    @Test void emitsExactCanonicalBytesAndReadsBackEverySequenceItemsetAndItem() throws Exception {
        var input = input(customer("a", order("o1", 1, 2), order("o2", 3)), customer("b", order("o3", 2, 12)));
        byte[] bytes = serializer.serialize(input);
        assertArrayEquals("1 2 -1 3 -1 -2\n2 12 -1 -2\n".getBytes(StandardCharsets.UTF_8), bytes);
        assertExactReadback(input, bytes);
    }

    @Test void singleItemAndMaximumJavaIntRemainExactDecimalTokens() throws Exception {
        var input = input(customer("a", order("o1", 1, Integer.MAX_VALUE)));
        byte[] bytes = serializer.serialize(input);
        assertEquals("1 2147483647 -1 -2\n", new String(bytes, StandardCharsets.UTF_8));
        assertExactReadback(input, bytes);
    }

    @Test void itemsetAndSequenceBoundariesCannotBeConfused() throws Exception {
        byte[] together = serializer.serialize(input(customer("a", order("o1", 1, 2))));
        byte[] sequential = serializer.serialize(input(customer("a", order("o1", 1), order("o2", 2))));
        byte[] separate = serializer.serialize(input(customer("a", order("o1", 1)), customer("b", order("o2", 2))));
        assertEquals("1 2 -1 -2\n", new String(together, StandardCharsets.UTF_8));
        assertEquals("1 -1 2 -1 -2\n", new String(sequential, StandardCharsets.UTF_8));
        assertEquals("1 -1 -2\n2 -1 -2\n", new String(separate, StandardCharsets.UTF_8));
        assertEquals(Sequence.of(Itemset.of(new Item(1), new Item(2))), parse(together).records().getFirst().sequence());
        assertEquals(Sequence.of(Itemset.of(new Item(1)), Itemset.of(new Item(2))), parse(sequential).records().getFirst().sequence());
        assertNotEquals(parse(together).records().getFirst().sequence(), parse(sequential).records().getFirst().sequence());
        assertEquals(List.of(1, 2), parse(separate).records().stream().map(r -> r.sid()).toList());
    }

    @Test void repeatedItemsInDifferentOrdersAndCustomersArePreserved() throws Exception {
        var input = input(customer("a", order("o1", 7), order("o2", 7)), customer("b", order("o3", 7)));
        byte[] bytes = serializer.serialize(input);
        assertEquals("7 -1 7 -1 -2\n7 -1 -2\n", new String(bytes, StandardCharsets.UTF_8));
        assertExactReadback(input, bytes);
    }

    @Test void numericItemOrderAndProvidedCustomerOrderAreNotReinterpreted() throws Exception {
        // Serialization preserves its ordered handoff; it does not perform another temporal/customer sort.
        var input = input(customer("z", order("later", 2, 10, 27)), customer("a", order("earlier", 1)));
        byte[] bytes = serializer.serialize(input);
        assertEquals("2 10 27 -1 -2\n1 -1 -2\n", new String(bytes, StandardCharsets.UTF_8));
        assertExactReadback(input, bytes);
    }

    @Test void outputIsAsciiCompatibleUtf8WithLfNoBomHeaderIdentityOrTrailingSpaces() {
        byte[] bytes = serializer.serialize(input(customer("private-customer 😀", order("private-order", 12))));
        assertEquals("12 -1 -2\n", new String(bytes, StandardCharsets.UTF_8));
        for (byte value : bytes) assertTrue(value == '\n' || value == ' ' || value == '-' || value >= '0' && value <= '9');
        assertFalse(new String(bytes, StandardCharsets.UTF_8).contains(" \n"));
        assertFalse(new String(bytes, StandardCharsets.UTF_8).contains("\r"));
    }

    @Test void payloadQuantityPriceAndCurrencyDoNotEnterSerializedTokens() {
        var original = order("o", 12);
        var other = new GroupedDataset.GroupedOrder("o", original.orderedAt(), "USD", original.itemIds(),
                List.of(new ParsedCsvRecord(99, 100, 105, List.of("different raw payload"))));
        assertArrayEquals(serializer.serialize(input(customer("a", original))), serializer.serialize(input(customer("a", other))));
    }

    @Test void eachCallOwnsItsBytesAndLeavesTheImmutableInputUntouched() {
        var order = order("o", 1, 2);
        var input = input(customer("a", order));
        byte[] first = serializer.serialize(input);
        byte[] second = serializer.serialize(input);
        assertNotSame(first, second);
        first[0] = '9';
        assertEquals("1 2 -1 -2\n", new String(second, StandardCharsets.UTF_8));
        assertArrayEquals(second, serializer.serialize(input));
        assertSame(order, input.customers().getFirst().orders().getFirst());
        assertEquals(List.of(1, 2), order.itemIds());
        assertEquals("Tên lịch sử 😀", order.sourceRecords().getFirst().fields().get(7));
    }

    @Test void shuffledRawRowsThroughAllPreprocessingStagesHaveIdenticalSpmfBytes() throws Exception {
        var rows = new ArrayList<ParsedCsvRecord>();
        String[][] data = {{"b", "later", "2026-11-02T00:00:00.000000Z", "p"},
                {"a", "o2", TIME, "q"}, {"a", "o1", TIME, "p"}, {"a", "o1", TIME, "q"}};
        for (var values : data) {
            long number = rows.size() + 2L;
            rows.add(new ParsedCsvRecord(number, number, number, List.of(values[1], Long.toString(number), values[0],
                    values[2], "COMPLETED", values[3], "old", "Old", "1", "100.00", "0.00", "100.00", "VND")));
        }
        var mapping = new ProductItemDictionary(Map.of("p", 27, "q", 12));
        var random = new Random(304);
        byte[] expected = "12 27 -1 12 -1 -2\n27 -1 -2\n".getBytes(StandardCharsets.UTF_8);
        for (int attempt = 0; attempt < 8; attempt++) {
            Collections.shuffle(rows, random);
            var grouped = new CustomerGroupingService().group(new VerifiedConversionInput(new byte[0], null, rows), mapping);
            var ordered = new TemporalOrderingService().order(grouped);
            byte[] bytes = serializer.serialize(ordered);
            assertArrayEquals(expected, bytes);
            assertExactReadback(ordered, bytes);
        }
    }

    @Test void emptyUtilityDatasetProducesNoBytesAndNoSequences() throws Exception {
        byte[] bytes = serializer.serialize(input());
        assertArrayEquals(new byte[0], bytes);
        assertEquals(0, parse(bytes).size());
        assertThrows(NullPointerException.class, () -> serializer.serialize(null));
    }

    @Test void emptyItemsetOrSequenceAndDuplicateIdentityFailClosed() {
        rejected(input(customer("a")));
        rejected(input(customer("a", order("o"))));
        rejected(input(customer(" ", order("o", 1))));
        rejected(input(customer("a", order(" ", 1))));
        rejected(input(customer("a", order("o1", 1)), customer("a", order("o2", 2))));
        rejected(input(customer("a", order("o", 1)), customer("b", order("o", 2))));
    }

    @Test void invalidNegativeZeroDuplicateOrUnsortedItemIdsAreNeverRepaired() {
        for (var values : List.of(List.of(-1), List.of(-2), List.of(-3), List.of(0), List.of(2, 1), List.of(1, 1))) {
            rejected(input(customer("a", order("o", values.toArray(Integer[]::new)))));
        }
    }

    @Test void failedSerializationDoesNotPublishPartialBytesOrPolluteFollowingCall() {
        rejected(input(customer("a", order("valid", 1)), customer("b", order("invalid", -1))));
        assertEquals("2 -1 -2\n", new String(serializer.serialize(input(customer("c", order("fresh", 2)))), StandardCharsets.UTF_8));
    }

    private void rejected(OrderedDataset input) {
        var failure = assertThrows(ApplicationException.class, () -> serializer.serialize(input));
        assertEquals(ErrorCode.INVALID_RESOURCE_STATE, failure.errorCode());
        assertEquals("Ordered SPMF input is inconsistent.", failure.getMessage());
    }
}
