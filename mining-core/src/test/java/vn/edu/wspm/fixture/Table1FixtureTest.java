package vn.edu.wspm.fixture;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;

class Table1FixtureTest {

    private static final Item A = new Item(1);
    private static final Item B = new Item(2);
    private static final Item C = new Item(3);
    private static final Item D = new Item(4);
    private static final Item E = new Item(5);
    private static final Item F = new Item(6);
    private static final Item G = new Item(7);
    private static final Item H = new Item(8);

    private static SequenceDatabase database;

    @BeforeAll
    static void parsePaperFixture() throws IOException, URISyntaxException {
        var resource = requireNonNull(
                Table1FixtureTest.class.getResource("/datasets/wspm/table1.spmf"));
        database = new SpmfSequenceParser().parse(Path.of(resource.toURI()));
    }

    @Test
    void usesDocumentedNumericLabelMappingAndContainsAllEightItems() {
        assertEquals(Map.of(
                'a', 1, 'b', 2, 'c', 3, 'd', 4,
                'e', 5, 'f', 6, 'g', 7, 'h', 8),
                Map.of(
                        'a', A.id(), 'b', B.id(), 'c', C.id(), 'd', D.id(),
                        'e', E.id(), 'f', F.id(), 'g', G.id(), 'h', H.id()));

        var distinctItems = database.records().stream()
                .flatMap(record -> record.sequence().itemsets().stream())
                .flatMap(itemset -> itemset.items().stream())
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(Set.of(A, B, C, D, E, F, G, H), distinctItems);
        assertEquals(8, distinctItems.size());
        assertEquals(7, database.size());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7),
                database.records().stream().map(record -> record.sid()).toList());
    }

    @Test
    void matchesEveryPaperSequenceLengthAndTotalDatabaseLength() {
        var lengths = database.records().stream()
                .map(record -> record.sequence().length())
                .toList();
        var sizes = database.records().stream()
                .map(record -> record.sequence().size())
                .toList();

        assertEquals(List.of(11, 10, 8, 9, 12, 7, 9), lengths);
        assertEquals(List.of(5, 6, 5, 6, 6, 4, 8), sizes);
        assertEquals(66, lengths.stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void matchesPaperOccurrencesOfItemA() {
        var occurrences = database.records().stream()
                .map(record -> countOccurrences(record.sequence(), A))
                .toList();

        assertEquals(List.of(5, 2, 1, 2, 2, 3, 8), occurrences);
        assertEquals(23, occurrences.stream().mapToInt(Integer::intValue).sum());
        assertEquals(7L, occurrences.stream().filter(count -> count > 0).count());
    }

    @Test
    void preservesExactStructureOfSidOne() {
        assertEquals(Sequence.of(
                Itemset.of(A),
                Itemset.of(A, B, C),
                Itemset.of(A, C),
                Itemset.of(A, D),
                Itemset.of(A, C, F)),
                database.records().get(0).sequence());
    }

    @Test
    void preservesExactStructureAndRepeatedAOfSidSeven() {
        var sidSeven = database.records().get(6).sequence();

        assertEquals(Sequence.of(
                Itemset.of(A),
                Itemset.of(A, B),
                Itemset.of(A),
                Itemset.of(A),
                Itemset.of(A),
                Itemset.of(A),
                Itemset.of(A),
                Itemset.of(A)),
                sidSeven);
        assertEquals(8, sidSeven.size());
        assertEquals(9, sidSeven.length());
        assertEquals(8, countOccurrences(sidSeven, A));
    }

    @Test
    void matchesPaperSupportOracleForAThenBc() {
        var pattern = List.of(Itemset.of(A), Itemset.of(B, C));
        var containingSids = database.records().stream()
                .filter(record -> containsAsOrderedSubsetForOracle(record.sequence(), pattern))
                .map(record -> record.sid())
                .toList();

        assertEquals(List.of(1, 2, 4), containingSids);
        assertEquals(3, containingSids.size());
    }

    private static int countOccurrences(Sequence sequence, Item item) {
        return (int) sequence.itemsets().stream()
                .flatMap(itemset -> itemset.items().stream())
                .filter(item::equals)
                .count();
    }

    /** TEST ORACLE ONLY: independent ordered-subset check; not a production support engine. */
    private static boolean containsAsOrderedSubsetForOracle(
            Sequence sequence, List<Itemset> pattern) {
        int patternIndex = 0;
        for (var sequenceItemset : sequence.itemsets()) {
            if (sequenceItemset.items().containsAll(pattern.get(patternIndex).items())) {
                patternIndex++;
                if (patternIndex == pattern.size()) {
                    return true;
                }
            }
        }
        return false;
    }
}
