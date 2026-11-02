package vn.edu.wspm.bitprune.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.bitprune.mining.BitPruneMiner;
import vn.edu.wspm.bitprune.statistics.BitPruneStatistics;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;
import vn.edu.wspm.tree.ExtensionType;
import vn.edu.wspm.tree.PrefixTree;

/**
 * Unit and boundary contract tests for {@link BitPruneOutputEntry} and {@link BitPruneOutputProjection} (C14.1).
 *
 * <p>Validates deterministic structural pre-order formatting, line syntax, LF separator rules,
 * immutability, empty result behavior, locale independence, and zero disk I/O.</p>
 */
class BitPruneOutputProjectionTest {

    private static final String TABLE1_SPMF =
            "1 -1 1 2 3 -1 1 3 -1 1 4 -1 1 3 6 -1 -2\n"
            + "1 2 4 -1 3 -1 2 3 -1 1 5 -1 2 -1 3 -1 -2\n"
            + "5 6 -1 1 2 -1 4 6 -1 3 -1 2 -1 -2\n"
            + "5 -1 7 -1 1 6 -1 1 2 3 -1 2 -1 3 -1 -2\n"
            + "1 -1 1 2 7 -1 3 4 7 -1 5 7 -1 7 -1 7 8 -1 -2\n"
            + "1 -1 1 2 4 -1 2 -1 1 3 -1 -2\n"
            + "1 -1 1 2 -1 1 -1 1 -1 1 -1 1 -1 1 -1 1 -1 -2\n";

    private final Item item1 = new Item(1);
    private final Item item2 = new Item(2);
    private final Item item3 = new Item(3);

    private final Pattern p1 = Pattern.singleton(item1);
    private final Pattern p2 = Pattern.singleton(item2);
    private final Pattern p3 = Pattern.singleton(item3);
    private final Pattern p12I = Pattern.of(Itemset.of(item1, item2));
    private final Pattern p1_2S = Pattern.of(Itemset.of(item1), Itemset.of(item2));

    private PrefixTree createSampleTree() {
        PrefixTree tree = new PrefixTree();
        tree.insert(Pattern.empty(), p1, ExtensionType.S_EXTENSION);
        tree.insert(p1, p12I, ExtensionType.I_EXTENSION);
        tree.insert(Pattern.empty(), p2, ExtensionType.S_EXTENSION);
        tree.freeze();
        return tree;
    }

    private BitPruneStatistics createSampleStatistics(long sequenceCount, long f1Count, long patternCount) {
        return new BitPruneStatistics(
                sequenceCount,
                f1Count,
                1,
                1,
                0,
                0,
                0,
                0,
                1,
                3,
                patternCount,
                1);
    }

    @Test
    @DisplayName("1. BitPruneOutputEntry constructor rejects null pattern")
    void testEntryRejectsNullPattern() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new BitPruneOutputEntry(null, 5));
        assertTrue(ex.getMessage().contains("pattern"));
    }

    @Test
    @DisplayName("2. BitPruneOutputEntry constructor rejects negative support")
    void testEntryRejectsNegativeSupport() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneOutputEntry(p1, -1));
        assertTrue(ex.getMessage().contains("support"));
    }

    @Test
    @DisplayName("3. BitPruneOutputEntry formats singleton pattern correctly")
    void testEntryFormatsSingleton() {
        BitPruneOutputEntry entry = new BitPruneOutputEntry(p1, 7);
        assertEquals("<(1)> #SUP: 7", entry.formatLine());
        assertEquals(p1, entry.pattern());
        assertEquals(7, entry.support());
    }

    @Test
    @DisplayName("4. BitPruneOutputEntry formats I-extension pattern correctly")
    void testEntryFormatsIExtension() {
        BitPruneOutputEntry entry = new BitPruneOutputEntry(p12I, 7);
        assertEquals("<(1 2)> #SUP: 7", entry.formatLine());
    }

    @Test
    @DisplayName("5. BitPruneOutputEntry formats S-extension pattern correctly")
    void testEntryFormatsSExtension() {
        BitPruneOutputEntry entry = new BitPruneOutputEntry(p1_2S, 7);
        assertEquals("<(1)(2)> #SUP: 7", entry.formatLine());
    }

    @Test
    @DisplayName("6. BitPruneOutputEntry supports zero support and exact absolute integer formatting")
    void testEntryExactAbsoluteSupport() {
        BitPruneOutputEntry entryZero = new BitPruneOutputEntry(p1, 0);
        assertEquals("<(1)> #SUP: 0", entryZero.formatLine());

        BitPruneOutputEntry entryLarge = new BitPruneOutputEntry(p1, 1000000);
        assertEquals("<(1)> #SUP: 1000000", entryLarge.formatLine());
        assertFalse(entryLarge.formatLine().contains("."));
        assertFalse(entryLarge.formatLine().contains(","));
    }

    @Test
    @DisplayName("7. BitPruneOutputProjection.from rejects null source result")
    void testProjectionRejectsNullResult() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> BitPruneOutputProjection.from(null));
        assertTrue(ex.getMessage().contains("result"));
    }

    @Test
    @DisplayName("8. BitPruneOutputProjection preserves exact deterministic PrefixTree pre-order")
    void testProjectionPreservesPrefixTreePreOrder() {
        PrefixTree tree = createSampleTree();
        // PrefixTree pre-order: root -> p1 -> p12I -> p2
        // Non-root pre-order: [p1, p12I, p2]
        Map<Pattern, Integer> supports = Map.of(p1, 10, p12I, 5, p2, 8);
        BitPruneStatistics stats = createSampleStatistics(10, 2, 3);
        BitPruneMiningResult result = new BitPruneMiningResult(tree, supports, stats);

        BitPruneOutputProjection projection = BitPruneOutputProjection.from(result);

        List<String> expectedLines = List.of(
                "<(1)> #SUP: 10",
                "<(1 2)> #SUP: 5",
                "<(2)> #SUP: 8");

        assertEquals(expectedLines, projection.lines());
        assertEquals(3, projection.entries().size());
        assertEquals(p1, projection.entries().get(0).pattern());
        assertEquals(10, projection.entries().get(0).support());
        assertEquals(p12I, projection.entries().get(1).pattern());
        assertEquals(5, projection.entries().get(1).support());
        assertEquals(p2, projection.entries().get(2).pattern());
        assertEquals(8, projection.entries().get(2).support());
    }

    @Test
    @DisplayName("9. Projection output order does not depend on support map iteration order")
    void testOutputOrderIndependentOfSupportsMapIterationOrder() {
        PrefixTree tree = createSampleTree();
        // Insert into supports map in reversed order: p2 -> p12I -> p1
        Map<Pattern, Integer> reversedSupports = new LinkedHashMap<>();
        reversedSupports.put(p2, 8);
        reversedSupports.put(p12I, 5);
        reversedSupports.put(p1, 10);

        BitPruneStatistics stats = createSampleStatistics(10, 2, 3);
        BitPruneMiningResult result = new BitPruneMiningResult(tree, reversedSupports, stats);

        BitPruneOutputProjection projection = BitPruneOutputProjection.from(result);

        // Resulting projection MUST still follow PrefixTree pre-order: p1 -> p12I -> p2
        List<String> expectedLines = List.of(
                "<(1)> #SUP: 10",
                "<(1 2)> #SUP: 5",
                "<(2)> #SUP: 8");
        assertEquals(expectedLines, projection.lines());
    }

    @Test
    @DisplayName("10. Projection entries and lines are strictly immutable")
    void testProjectionCollectionsAreImmutable() {
        PrefixTree tree = createSampleTree();
        Map<Pattern, Integer> supports = Map.of(p1, 10, p12I, 5, p2, 8);
        BitPruneStatistics stats = createSampleStatistics(10, 2, 3);
        BitPruneMiningResult result = new BitPruneMiningResult(tree, supports, stats);

        BitPruneOutputProjection projection = BitPruneOutputProjection.from(result);

        assertThrows(UnsupportedOperationException.class, () ->
                projection.entries().add(new BitPruneOutputEntry(p3, 1)));

        assertThrows(UnsupportedOperationException.class, () ->
                projection.entries().clear());

        assertThrows(UnsupportedOperationException.class, () ->
                projection.lines().add("<(3)> #SUP: 1"));

        assertThrows(UnsupportedOperationException.class, () ->
                projection.lines().clear());
    }

    @Test
    @DisplayName("11. Repeated entries(), lines(), and content() access return identical values")
    void testRepeatedAccessConsistency() {
        PrefixTree tree = createSampleTree();
        Map<Pattern, Integer> supports = Map.of(p1, 10, p12I, 5, p2, 8);
        BitPruneStatistics stats = createSampleStatistics(10, 2, 3);
        BitPruneMiningResult result = new BitPruneMiningResult(tree, supports, stats);

        BitPruneOutputProjection projection = BitPruneOutputProjection.from(result);

        assertSame(projection.entries(), projection.entries());
        assertSame(projection.lines(), projection.lines());
        assertSame(projection.content(), projection.content());
    }

    @Test
    @DisplayName("12. Repeated projection construction from same result yields equal projections")
    void testRepeatedConstructionEquality() {
        PrefixTree tree = createSampleTree();
        Map<Pattern, Integer> supports = Map.of(p1, 10, p12I, 5, p2, 8);
        BitPruneStatistics stats = createSampleStatistics(10, 2, 3);
        BitPruneMiningResult result = new BitPruneMiningResult(tree, supports, stats);

        BitPruneOutputProjection p1 = BitPruneOutputProjection.from(result);
        BitPruneOutputProjection p2 = BitPruneOutputProjection.from(result);

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
        assertEquals(p1.content(), p2.content());
        assertEquals(p1.lines(), p2.lines());
    }

    @Test
    @DisplayName("13. Content uses LF line separators only and has exactly one trailing LF for non-empty output")
    void testLfOnlySeparatorAndTrailingLf() {
        PrefixTree tree = createSampleTree();
        Map<Pattern, Integer> supports = Map.of(p1, 10, p12I, 5, p2, 8);
        BitPruneStatistics stats = createSampleStatistics(10, 2, 3);
        BitPruneMiningResult result = new BitPruneMiningResult(tree, supports, stats);

        BitPruneOutputProjection projection = BitPruneOutputProjection.from(result);
        String content = projection.content();

        assertFalse(content.contains("\r"), "content must not contain CR or CRLF");
        assertTrue(content.endsWith("\n"), "non-empty content must end with LF");
        assertFalse(content.endsWith("\n\n"), "non-empty content must not end with multiple LFs");

        long newlineCount = content.chars().filter(ch -> ch == '\n').count();
        assertEquals(3, newlineCount, "LF count must exactly match number of lines");
        assertEquals("<(1)> #SUP: 10\n<(1 2)> #SUP: 5\n<(2)> #SUP: 8\n", content);
    }

    @Test
    @DisplayName("14. Empty result produces empty entries, empty lines, and empty content")
    void testEmptyResultProducesEmptyContent() {
        PrefixTree tree = new PrefixTree();
        tree.freeze();
        BitPruneStatistics stats = new BitPruneStatistics(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        BitPruneMiningResult result = new BitPruneMiningResult(tree, Map.of(), stats);

        BitPruneOutputProjection projection = BitPruneOutputProjection.from(result);

        assertTrue(projection.entries().isEmpty());
        assertTrue(projection.lines().isEmpty());
        assertEquals("", projection.content());
    }

    @Test
    @DisplayName("15. Empty database with valid absolute minsup produces empty projection")
    void testEmptyDatabaseMiningProducesEmptyProjection() {
        BitPruneMiner miner = new BitPruneMiner();
        BitPruneMiningResult result = miner.mine(new SequenceDatabase(List.of()), MinimumSupport.absolute(1.0));

        BitPruneOutputProjection projection = BitPruneOutputProjection.from(result);

        assertTrue(projection.entries().isEmpty());
        assertTrue(projection.lines().isEmpty());
        assertEquals("", projection.content());
    }

    @Test
    @DisplayName("16. Non-empty database with F1-empty result produces empty projection")
    void testF1EmptyDatabaseProducesEmptyProjection() {
        BitPruneMiner miner = new BitPruneMiner();
        var sequence = new Sequence(List.of(Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, sequence)));

        // Set threshold far above database size so F1 is empty
        BitPruneMiningResult result = miner.mine(db, MinimumSupport.absolute(100.0));

        BitPruneOutputProjection projection = BitPruneOutputProjection.from(result);

        assertTrue(projection.entries().isEmpty());
        assertTrue(projection.lines().isEmpty());
        assertEquals("", projection.content());
    }

    @Test
    @DisplayName("17. Non-empty output contains no duplicate entries or duplicate lines")
    void testNoDuplicateEntriesOrLines() {
        PrefixTree tree = createSampleTree();
        Map<Pattern, Integer> supports = Map.of(p1, 10, p12I, 5, p2, 8);
        BitPruneStatistics stats = createSampleStatistics(10, 2, 3);
        BitPruneMiningResult result = new BitPruneMiningResult(tree, supports, stats);

        BitPruneOutputProjection projection = BitPruneOutputProjection.from(result);

        Set<BitPruneOutputEntry> entrySet = new HashSet<>(projection.entries());
        assertEquals(projection.entries().size(), entrySet.size());

        Set<String> lineSet = new HashSet<>(projection.lines());
        assertEquals(projection.lines().size(), lineSet.size());
    }

    @Test
    @DisplayName("18. Formatting is locale independent across different default locales")
    void testLocaleIndependence() {
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale[] testLocales = new Locale[] {
                    Locale.GERMANY,
                    Locale.FRANCE,
                    new Locale("ar"),
                    new Locale("tr")
            };

            for (Locale locale : testLocales) {
                Locale.setDefault(locale);

                BitPruneOutputEntry entry = new BitPruneOutputEntry(p1, 12345);
                assertEquals("<(1)> #SUP: 12345", entry.formatLine());

                PrefixTree tree = createSampleTree();
                Map<Pattern, Integer> supports = Map.of(p1, 10, p12I, 5, p2, 8);
                BitPruneStatistics stats = createSampleStatistics(10, 2, 3);
                BitPruneMiningResult result = new BitPruneMiningResult(tree, supports, stats);

                BitPruneOutputProjection projection = BitPruneOutputProjection.from(result);
                assertEquals("<(1)> #SUP: 10\n<(1 2)> #SUP: 5\n<(2)> #SUP: 8\n", projection.content());
            }
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    @DisplayName("19. Output projection contains no console headings, metadata, or statistics")
    void testNoConsoleHeadingsOrStatistics() {
        PrefixTree tree = createSampleTree();
        Map<Pattern, Integer> supports = Map.of(p1, 10, p12I, 5, p2, 8);
        BitPruneStatistics stats = createSampleStatistics(10, 2, 3);
        BitPruneMiningResult result = new BitPruneMiningResult(tree, supports, stats);

        BitPruneOutputProjection projection = BitPruneOutputProjection.from(result);
        String content = projection.content();

        assertFalse(content.contains("="));
        assertFalse(content.contains("INPUT CONFIGURATION"));
        assertFalse(content.contains("MINING STATISTICS"));
        assertFalse(content.contains("MEMORY STATISTICS"));
        assertFalse(content.contains("TIME STATISTICS"));
        assertFalse(content.contains("Candidates"));
        assertFalse(content.contains("Expanded Nodes"));
        assertFalse(content.contains("Dataset"));
    }

    @Test
    @DisplayName("20. Creating projection leaves source BitPruneMiningResult unchanged")
    void testSourceMiningResultUnchanged() {
        PrefixTree tree = createSampleTree();
        Map<Pattern, Integer> supports = Map.of(p1, 10, p12I, 5, p2, 8);
        BitPruneStatistics stats = createSampleStatistics(10, 2, 3);
        BitPruneMiningResult result = new BitPruneMiningResult(tree, supports, stats);

        List<Pattern> patternsBefore = List.copyOf(result.acceptedPatterns());
        Map<Pattern, Integer> supportsBefore = Map.copyOf(result.supports());
        BitPruneStatistics statsBefore = result.statistics();

        BitPruneOutputProjection projection = BitPruneOutputProjection.from(result);
        assertNotNull(projection);

        assertEquals(patternsBefore, result.acceptedPatterns());
        assertEquals(supportsBefore, result.supports());
        assertEquals(statsBefore, result.statistics());
    }

    @Test
    @DisplayName("21. Exact Pattern object identity is preserved in output entries")
    void testExactPatternIdentityPreserved() {
        PrefixTree tree = createSampleTree();
        Map<Pattern, Integer> supports = Map.of(p1, 10, p12I, 5, p2, 8);
        BitPruneStatistics stats = createSampleStatistics(10, 2, 3);
        BitPruneMiningResult result = new BitPruneMiningResult(tree, supports, stats);

        BitPruneOutputProjection projection = BitPruneOutputProjection.from(result);

        for (int i = 0; i < result.acceptedPatterns().size(); i++) {
            assertSame(result.acceptedPatterns().get(i), projection.entries().get(i).pattern(),
                    "Entry pattern at index " + i + " must have exact object identity");
        }
    }

    @Test
    @DisplayName("22. Table 1 paper execution reproduces exact 9 published pattern lines in pre-order (0 disk I/O)")
    void testTable1ExactPublishedOutputLines() throws IOException {
        SequenceDatabase database = new SpmfSequenceParser().parse(new StringReader(TABLE1_SPMF));
        BitPruneMiner miner = new BitPruneMiner();
        BitPruneMiningResult result = miner.mine(database, MinimumSupport.percentage(14.0));

        BitPruneOutputProjection projection = BitPruneOutputProjection.from(result);

        List<String> expectedLines = List.of(
                "<(1)> #SUP: 7",
                "<(1 2)> #SUP: 7",
                "<(1 2)(3)> #SUP: 6",
                "<(1)(1)> #SUP: 6",
                "<(1)(2)> #SUP: 7",
                "<(1)(3)> #SUP: 6",
                "<(2)> #SUP: 7",
                "<(2)(3)> #SUP: 6",
                "<(3)> #SUP: 6");

        assertEquals(expectedLines, projection.lines());
        assertEquals(9, projection.entries().size());

        StringBuilder expectedContent = new StringBuilder();
        for (String line : expectedLines) {
            expectedContent.append(line).append('\n');
        }
        assertEquals(expectedContent.toString(), projection.content());
    }

    @Test
    @DisplayName("23. BitPruneOutputEntry and BitPruneOutputProjection toString and equals contracts")
    void testToStringAndEqualsContracts() {
        BitPruneOutputEntry e1 = new BitPruneOutputEntry(p1, 7);
        BitPruneOutputEntry e2 = new BitPruneOutputEntry(p1, 7);
        BitPruneOutputEntry e3 = new BitPruneOutputEntry(p2, 7);

        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
        assertFalse(e1.equals(e3));
        assertFalse(e1.equals(null));
        assertFalse(e1.equals("string"));
        assertNotNull(e1.toString());

        PrefixTree tree = createSampleTree();
        Map<Pattern, Integer> supports = Map.of(p1, 10, p12I, 5, p2, 8);
        BitPruneStatistics stats = createSampleStatistics(10, 2, 3);
        BitPruneMiningResult result = new BitPruneMiningResult(tree, supports, stats);

        BitPruneOutputProjection projection = BitPruneOutputProjection.from(result);
        assertEquals("BitPruneOutputProjection[entries=3]", projection.toString());
        assertFalse(projection.equals(null));
        assertFalse(projection.equals("different_type"));
    }
}
