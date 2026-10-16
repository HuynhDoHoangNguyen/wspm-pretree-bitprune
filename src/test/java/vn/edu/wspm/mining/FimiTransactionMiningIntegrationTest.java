package vn.edu.wspm.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.FimiTransactionParser;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;

/** Focused B19.1 parser-to-miner integration and independent differential check. */
class FimiTransactionMiningIntegrationTest {

    private final FimiTransactionParser parser = new FimiTransactionParser();
    private final WspmPreTreeMiner miner = new WspmPreTreeMiner();
    private final BruteForceWeightedPatternOracle oracle =
            new BruteForceWeightedPatternOracle();

    @Test
    void oneItemsetTransactionsSupportIExtensionButNotInventedSExtension()
            throws IOException {
        var database = parser.parse(new StringReader("1 2 3\n1 2\n1 3\n"));
        var threshold = MinimumSupport.absolute(0.5);
        var sameItemset = pattern(itemset(1, 2));
        var temporal = pattern(itemset(1), itemset(2));

        var expected = oracle.mine(database, threshold);
        var actualResult = miner.mine(database, threshold);
        var actual = actualResult.supports();

        assertEquals(2, oracle.countSupport(database, sameItemset));
        assertEquals(0, oracle.countSupport(database, temporal));
        assertEquals(expected, actual, "full FIMI-derived pattern/support map");
        assertEquals(2, actual.get(sameItemset));
        assertTrue(actual.containsKey(sameItemset));
        assertFalse(actual.containsKey(temporal));
        assertTrue(actualResult.prefixTree().find(temporal).isEmpty());
        var statistics = actualResult.statistics();
        assertEquals(3, statistics.sequenceCount());
        assertEquals(statistics.candidateCount(),
                statistics.iCandidateCount() + statistics.sCandidateCount());
        assertEquals(statistics.candidateCount(),
                statistics.acceptedCandidateCount() + statistics.rejectedCandidateCount());
        assertEquals(statistics.patternCount(), actual.size());
        assertEquals(statistics.patternCount(), actualResult.prefixTree().nodeCount() - 1);
        assertTrue(statistics.sCandidateCount() > 0);
    }

    private static Pattern pattern(Itemset... itemsets) {
        return Pattern.of(itemsets);
    }

    private static Itemset itemset(int... ids) {
        return new Itemset(Arrays.stream(ids).mapToObj(Item::new).toList());
    }
}
