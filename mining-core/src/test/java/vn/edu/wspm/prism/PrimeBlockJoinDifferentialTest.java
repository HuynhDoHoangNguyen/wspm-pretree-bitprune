package vn.edu.wspm.prism;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.PatternExtensions;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;

/** Differential tests against a direct structural subsequence oracle independent of PRISM joins. */
class PrimeBlockJoinDifferentialTest {

    private static final Item A = new Item(1);
    private static final Item B = new Item(2);
    private static final Item C = new Item(3);
    private static final Item D = new Item(4);

    private static final PatternExtensions EXTENSIONS = new PatternExtensions();
    private static SequenceDatabase table1;

    @BeforeAll
    static void loadTable1() throws IOException, URISyntaxException {
        var resource = requireNonNull(
                PrimeBlockJoinDifferentialTest.class.getResource(
                        "/datasets/wspm/table1.spmf"));
        table1 = new SpmfSequenceParser().parse(Path.of(resource.toURI()));
    }

    @Test
    void differentialIExtensionsMatchDirectOracleIncludingZeroSupportAndBlockBoundary() {
        SequenceDatabase database = boundaryDatabase();

        assertIExtension(database, Pattern.singleton(A), B);
        assertIExtension(database, Pattern.singleton(A), C);
        assertIExtension(database, Pattern.of(Itemset.of(A), Itemset.of(B)), C);
        assertIExtension(database, Pattern.singleton(C), D); // support zero
    }

    @Test
    void differentialSExtensionsMatchDirectOracleIncludingRepeatedItemAndZeroSupport() {
        SequenceDatabase database = boundaryDatabase();

        assertSExtension(database, Pattern.singleton(A), A); // <(a)(a)>
        assertSExtension(database, Pattern.singleton(A), B);
        assertSExtension(database, Pattern.singleton(B), C);
        assertSExtension(database, Pattern.singleton(D), A); // support zero
    }

    @Test
    void table1RequiredExtensionsMatchOracleByEndpointsSidsAndSupport() {
        assertSExtension(table1, Pattern.singleton(A), A);
        assertSExtension(table1, Pattern.singleton(A), B);
        assertSExtension(table1, Pattern.singleton(A), C);
        assertIExtension(table1, Pattern.singleton(A), B);

        assertEquals(6, oracleEncoding(table1,
                Pattern.of(Itemset.of(A), Itemset.of(A))).support());
        assertEquals(7, oracleEncoding(table1,
                Pattern.of(Itemset.of(A), Itemset.of(B))).support());
        assertEquals(6, oracleEncoding(table1,
                Pattern.of(Itemset.of(A), Itemset.of(C))).support());
        assertEquals(7, oracleEncoding(table1, Pattern.of(Itemset.of(A, B))).support());
    }

    @Test
    void table1ChainedJoinsReproduceAThenBcOracle() {
        Pattern a = Pattern.singleton(A);
        Pattern b = Pattern.singleton(B);
        Pattern c = Pattern.singleton(C);
        Pattern aThenB = EXTENSIONS.sExtend(a, B);
        Pattern aThenBc = EXTENSIONS.iExtend(aThenB, C);

        PatternEncoding aThenBEncoding = PrimeBlockJoins.sJoin(
                oracleEncoding(table1, a), oracleEncoding(table1, b));
        PatternEncoding actual = PrimeBlockJoins.iJoin(
                aThenBEncoding, oracleEncoding(table1, c));
        PatternEncoding expected = oracleEncoding(table1, aThenBc);

        assertEncodingEquals(expected, actual);
        assertEquals(3, actual.support());
        assertEquals(List.of(1, 2, 4), supportingSids(actual));
    }

    private static void assertIExtension(
            SequenceDatabase database, Pattern prefix, Item extensionItem) {
        Pattern extended = EXTENSIONS.iExtend(prefix, extensionItem);
        PatternEncoding actual = PrimeBlockJoins.iJoin(
                oracleEncoding(database, prefix),
                oracleEncoding(database, Pattern.singleton(extensionItem)));
        assertEncodingEquals(oracleEncoding(database, extended), actual);
    }

    private static void assertSExtension(
            SequenceDatabase database, Pattern prefix, Item extensionItem) {
        Pattern extended = EXTENSIONS.sExtend(prefix, extensionItem);
        PatternEncoding actual = PrimeBlockJoins.sJoin(
                oracleEncoding(database, prefix),
                oracleEncoding(database, Pattern.singleton(extensionItem)));
        assertEncodingEquals(oracleEncoding(database, extended), actual);
    }

    private static void assertEncodingEquals(
            PatternEncoding expected, PatternEncoding actual) {
        assertEquals(expected.support(), actual.support(), "support differs");
        assertEquals(supportingSids(expected), supportingSids(actual), "supporting SIDs differ");
        assertEquals(expected.positionBlocks(), actual.positionBlocks(), "endpoints differ");
        assertEquals(actual.sequenceBlock().support(), actual.support(),
                "support must come from the result SequenceBlock");
    }

    /**
     * TEST ORACLE ONLY. Computes every valid final itemset position by direct ordered-subset DP.
     * It does not call PrimeBlockJoins, strictAfter, GCD, or any production support routine.
     */
    private static PatternEncoding oracleEncoding(SequenceDatabase database, Pattern pattern) {
        int sidCount = database.size();
        var positionsBySid = new ArrayList<PositionBlock>(sidCount);
        var supporting = new ArrayList<Integer>();

        for (SequenceRecord record : database.records()) {
            List<Integer> endpoints = directEndpoints(record.sequence(), pattern);
            int[] endpointArray = endpoints.stream().mapToInt(Integer::intValue).toArray();
            PositionBlock positions = PositionBlock.of(
                    record.sid(), record.sequence().size(), endpointArray);
            positionsBySid.add(positions);
            if (!endpoints.isEmpty()) {
                supporting.add(record.sid());
            }
        }

        int[] supportingArray = supporting.stream().mapToInt(Integer::intValue).toArray();
        return new PatternEncoding(
                SequenceBlock.of(sidCount, supportingArray), positionsBySid);
    }

    private static List<Integer> directEndpoints(Sequence sequence, Pattern pattern) {
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("The B14 oracle requires a non-empty pattern");
        }
        boolean[] matchedPrefix = new boolean[pattern.size()];
        var endpoints = new ArrayList<Integer>();

        for (int sequenceIndex = 0; sequenceIndex < sequence.size(); sequenceIndex++) {
            Itemset sequenceItemset = sequence.itemsets().get(sequenceIndex);
            for (int patternIndex = pattern.size() - 1; patternIndex >= 0; patternIndex--) {
                if (sequenceItemset.items().containsAll(
                        pattern.itemsets().get(patternIndex).items())
                        && (patternIndex == 0 || matchedPrefix[patternIndex - 1])) {
                    matchedPrefix[patternIndex] = true;
                    if (patternIndex == pattern.size() - 1) {
                        endpoints.add(sequenceIndex + 1);
                    }
                }
            }
        }
        return List.copyOf(endpoints);
    }

    private static List<Integer> supportingSids(PatternEncoding encoding) {
        var result = new ArrayList<Integer>();
        for (int sid = 1; sid <= encoding.sequenceBlock().sidCount(); sid++) {
            if (encoding.sequenceBlock().isSupporting(sid)) {
                result.add(sid);
            }
        }
        return List.copyOf(result);
    }

    private static SequenceDatabase boundaryDatabase() {
        return new SequenceDatabase(List.of(
                new SequenceRecord(1, Sequence.of(
                        Itemset.of(A), Itemset.of(D), Itemset.of(A), Itemset.of(D),
                        Itemset.of(D), Itemset.of(D), Itemset.of(A), Itemset.of(A, B),
                        Itemset.of(B, C), Itemset.of(C))),
                new SequenceRecord(2, Sequence.of(
                        Itemset.of(B), Itemset.of(A, C), Itemset.of(A), Itemset.of(B),
                        Itemset.of(C))),
                new SequenceRecord(3, Sequence.of(
                        Itemset.of(C), Itemset.of(B), Itemset.of(A))),
                new SequenceRecord(4, Sequence.of(
                        Itemset.of(D), Itemset.of(D), Itemset.of(D)))));
    }
}
