package vn.edu.wspm.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Sequence;

class FimiTransactionParserTest {

    private final FimiTransactionParser parser = new FimiTransactionParser();

    @Test
    void parsesTransactionsFromPathWithOneItemsetPerSequence(@TempDir Path temporaryDirectory)
            throws IOException {
        Path input = temporaryDirectory.resolve("transactions.dat");
        Files.writeString(input, "1 15 18\n2 16 55\n1 15 74\n");

        var database = parser.parse(input);

        assertEquals(3, database.size());
        assertEquals(List.of(1, 2, 3),
                database.records().stream().map(record -> record.sid()).toList());
        assertEquals(List.of(
                Sequence.of(itemset(1, 15, 18)),
                Sequence.of(itemset(2, 16, 55)),
                Sequence.of(itemset(1, 15, 74))),
                database.records().stream().map(record -> record.sequence()).toList());
        assertTrue(database.records().stream().allMatch(record -> record.sequence().size() == 1));
        assertEquals(9, database.records().stream()
                .mapToInt(record -> record.sequence().length()).sum());
    }

    @Test
    void canonicalizesItemsWithoutRemappingOrInventingTemporalOrder() throws IOException {
        var database = parse("18 1 15");
        var sequence = database.records().getFirst().sequence();

        assertEquals(Sequence.of(itemset(1, 15, 18)), sequence);
        assertEquals(1, sequence.size());
        assertEquals(3, sequence.length());
    }

    @Test
    void acceptsWhitespaceCrLfLfAndSkipsBlankLines() throws IOException {
        var database = parse(" \t1   2\t3  \r\n\r\n  4\t5 \n\n 6  \r\n");

        assertEquals(3, database.size());
        assertEquals(List.of(1, 2, 3),
                database.records().stream().map(record -> record.sid()).toList());
        assertEquals(List.of(
                Sequence.of(itemset(1, 2, 3)),
                Sequence.of(itemset(4, 5)),
                Sequence.of(itemset(6))),
                database.records().stream().map(record -> record.sequence()).toList());
    }

    @Test
    void emptyAndWhitespaceOnlyInputProduceEmptyDatabase() throws IOException {
        assertEquals(0, parse("").size());
        assertEquals(0, parse(" \t\r\n\n").size());
    }

    @Test
    void rejectsDuplicateItemRatherThanSilentlyCollapsingIt() {
        var error = assertMalformed("1 2 1");

        assertTrue(error.getMessage().contains("line 1, token 3"));
        assertTrue(error.getMessage().contains("duplicate item in transaction: 1"));
    }

    @Test
    void rejectsNegativeIdsAndSpmfDelimiters() {
        assertTrue(assertMalformed("1 -3 2").getMessage().contains("negative item id"));
        assertTrue(assertMalformed("1 -1 2").getMessage().contains("SPMF delimiters"));
        assertTrue(assertMalformed("1 -2").getMessage().contains("SPMF delimiters"));
    }

    @Test
    void rejectsIntegerOverflowWithLocationAndToken() {
        var positive = assertMalformed("1 2147483648");
        var negative = assertMalformed("-2147483649");

        assertTrue(positive.getMessage().contains("line 1, token 2"));
        assertTrue(positive.getMessage().contains("outside the supported int range: 2147483648"));
        assertTrue(negative.getMessage().contains("outside the supported int range: -2147483649"));
    }

    @Test
    void rejectsNonIntegerToken() {
        var error = assertMalformed("1 abc 2");

        assertTrue(error.getMessage().contains("line 1, token 2"));
        assertTrue(error.getMessage().contains("token is not a valid integer: abc"));
    }

    @Test
    void preservesLargeIdsAndIntegerMaxValue() throws IOException {
        var database = parse("7113 2298 5114 3404 2147483647");

        assertEquals(List.of(2298, 3404, 5114, 7113, Integer.MAX_VALUE),
                database.records().getFirst().sequence().itemsets().getFirst().items().stream()
                        .map(Item::id).toList());
    }

    @Test
    void explicitFormatSelectorDelegatesWithoutAutoDetection() throws IOException {
        var selector = new SequenceDatabaseParser();

        var fimi = selector.parse(
                new StringReader("1 2"), SequenceDatabaseFormat.FIMI_TRANSACTION);
        var spmf = selector.parse(
                new StringReader("1 2 -1 -2"), SequenceDatabaseFormat.SPMF_SEQUENTIAL);

        assertEquals(Sequence.of(itemset(1, 2)), fimi.records().getFirst().sequence());
        assertEquals(Sequence.of(itemset(1, 2)), spmf.records().getFirst().sequence());
        assertThrows(SpmfParseException.class, () -> selector.parse(
                new StringReader("1 2"), SequenceDatabaseFormat.SPMF_SEQUENTIAL));
        assertThrows(FimiParseException.class, () -> selector.parse(
                new StringReader("1 2 -1 -2"), SequenceDatabaseFormat.FIMI_TRANSACTION));
    }

    private vn.edu.wspm.model.SequenceDatabase parse(String input) throws IOException {
        return parser.parse(new StringReader(input));
    }

    private FimiParseException assertMalformed(String input) {
        return assertThrows(FimiParseException.class, () -> parse(input));
    }

    private static Itemset itemset(int... ids) {
        return new Itemset(java.util.Arrays.stream(ids).mapToObj(Item::new).toList());
    }
}
