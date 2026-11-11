package vn.edu.wspm.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Sequence;

class SpmfSequenceParserTest {

    private final SpmfSequenceParser parser = new SpmfSequenceParser();

    @Test
    void parsesSingleItemsetFromPath() throws IOException, URISyntaxException {
        var resource = requireNonNull(getClass().getResource("/spmf/single-sequence.txt"));
        var input = Path.of(resource.toURI());

        var database = parser.parse(input);

        assertEquals(1, database.size());
        assertEquals(Sequence.of(Itemset.of(item(1), item(2), item(3))),
                database.records().getFirst().sequence());
    }

    @Test
    void preservesMultipleItemsetBoundariesAndTemporalOrder() throws IOException {
        var database = parse("1 2 -1 3 -1 4 5 -1 -2");

        assertEquals(Sequence.of(
                Itemset.of(item(1), item(2)),
                Itemset.of(item(3)),
                Itemset.of(item(4), item(5))),
                database.records().getFirst().sequence());
    }

    @Test
    void parsesMultipleSequencesAndAssignsDeterministicOneBasedSids() throws IOException {
        var database = parse("1 -1 2 -1 -2\n3 4 -1 -2");

        assertEquals(2, database.size());
        assertEquals(List.of(1, 2), database.records().stream().map(record -> record.sid()).toList());
        assertEquals(Sequence.of(Itemset.of(item(1)), Itemset.of(item(2))),
                database.records().get(0).sequence());
        assertEquals(Sequence.of(Itemset.of(item(3), item(4))),
                database.records().get(1).sequence());
    }

    @Test
    void preservesRepeatedItemAcrossDifferentItemsets() throws IOException {
        var database = parse("1 -1 1 -1 1 -1 -2");

        assertEquals(Sequence.of(
                Itemset.of(item(1)), Itemset.of(item(1)), Itemset.of(item(1))),
                database.records().getFirst().sequence());
    }

    @Test
    void rejectsDuplicateItemWithinSameItemset() {
        var error = assertMalformed("1 1 -1 -2");

        assertTrue(error.getMessage().contains("duplicate item in itemset: 1"));
    }

    @Test
    void rejectsMissingSequenceTerminator() {
        var error = assertMalformed("1 2 -1");

        assertTrue(error.getMessage().contains("missing sequence delimiter -2"));
    }

    @Test
    void rejectsMissingItemsetTerminator() {
        var error = assertMalformed("1 2 -2");

        assertTrue(error.getMessage().contains("missing -1 before sequence delimiter -2"));
    }

    @Test
    void parsesEmptyInputAsEmptyDatabase() throws IOException {
        assertEquals(0, parse("").size());
        assertEquals(0, parse(" \t\r\n").size());
    }

    @Test
    void treatsSpacesTabsAndLineBreaksAsEquivalentWhitespace() throws IOException {
        var database = parse(" \t1   2\n-1\r\n\t3 -1 \n -2 ");

        assertEquals(Sequence.of(
                Itemset.of(item(1), item(2)), Itemset.of(item(3))),
                database.records().getFirst().sequence());
    }

    @Test
    void allowsZeroItemId() throws IOException {
        var database = parse("0 1 -1 -2");

        assertEquals(List.of(item(0), item(1)),
                database.records().getFirst().sequence().itemsets().getFirst().items());
    }

    @Test
    void rejectsNegativeItemIdOtherThanReservedDelimiters() {
        var error = assertMalformed("1 -3 -1 -2");

        assertTrue(error.getMessage().contains("negative item id is not allowed: -3"));
    }

    @Test
    void acceptsIntegerMaxValueWithoutRemapping() throws IOException {
        var database = parse(Integer.MAX_VALUE + " -1 -2");

        assertEquals(Integer.MAX_VALUE, database.records().getFirst().sequence()
                .itemsets().getFirst().items().getFirst().id());
    }

    @Test
    void rejectsValueGreaterThanIntegerMaxValueAsOverflow() {
        var error = assertMalformed("2147483648 -1 -2");

        assertTrue(error.getMessage().contains("outside the supported int range"));
        assertTrue(error.getMessage().contains("2147483648"));
    }

    @Test
    void rejectsNonNumericToken() {
        var error = assertMalformed("abc -1 -2");

        assertTrue(error.getMessage().contains("not a valid integer: abc"));
    }

    @Test
    void rejectsEmptyItemsetsAndEmptySequences() {
        assertTrue(assertMalformed("-1 -2").getMessage().contains("empty itemset"));
        assertTrue(assertMalformed("1 -1 -1 -2").getMessage().contains("empty itemset"));
        assertTrue(assertMalformed("-2").getMessage().contains("empty sequence"));
    }

    private vn.edu.wspm.model.SequenceDatabase parse(String input) throws IOException {
        return parser.parse(new StringReader(input));
    }

    private SpmfParseException assertMalformed(String input) {
        return assertThrows(SpmfParseException.class, () -> parse(input));
    }

    private static Item item(int id) {
        return new Item(id);
    }
}
