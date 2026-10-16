package vn.edu.wspm.mining;

import static java.lang.reflect.Modifier.isFinal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;

class WspmMiningStatisticsTest {

    private final WspmPreTreeMiner miner = new WspmPreTreeMiner();

    @Test
    void emptyDatabaseHasZeroExecutionCounts() {
        var result = miner.mine(database(), MinimumSupport.absolute(0.0));
        var statistics = result.statistics();

        assertEquals(0, statistics.sequenceCount());
        assertEquals(0, statistics.f1Count());
        assertEquals(0, statistics.candidateCount());
        assertEquals(0, statistics.acceptedCandidateCount());
        assertEquals(0, statistics.rejectedCandidateCount());
        assertEquals(0, statistics.iCandidateCount());
        assertEquals(0, statistics.sCandidateCount());
        assertEquals(0, statistics.expandedNodeCount());
        assertEquals(0, statistics.patternCount());
        assertTrue(statistics.elapsedNanos() >= 0);
    }

    @Test
    void singleItemNodeIsExpandedButStructuralLengthBoundGeneratesNoCandidate() {
        var result = miner.mine(
                database(Sequence.of(itemset(1))), MinimumSupport.absolute(0.5));
        var statistics = result.statistics();

        assertEquals(1, statistics.sequenceCount());
        assertEquals(1, statistics.f1Count());
        assertEquals(1, statistics.patternCount());
        assertEquals(1, statistics.expandedNodeCount());
        assertEquals(0, statistics.candidateCount());
    }

    @Test
    void acceptedCandidateIsCountedOnlyAfterF1AndBecomesOneTreeNode() {
        var repeated = Pattern.of(itemset(1), itemset(1));
        var result = miner.mine(
                database(Sequence.of(itemset(1), itemset(1))),
                MinimumSupport.absolute(0.5));
        var statistics = result.statistics();

        assertEquals(1, statistics.f1Count());
        assertEquals(1, statistics.candidateCount());
        assertEquals(1, statistics.acceptedCandidateCount());
        assertEquals(0, statistics.rejectedCandidateCount());
        assertEquals(0, statistics.iCandidateCount());
        assertEquals(1, statistics.sCandidateCount());
        assertEquals(2, statistics.patternCount());
        assertEquals(2, statistics.expandedNodeCount());
        assertEquals(1, result.support(repeated));
    }

    @Test
    void rejectedCandidatesAreCountedButNeverInserted() {
        var rejected = Pattern.of(itemset(1, 2));
        var result = miner.mine(
                database(Sequence.of(itemset(1), itemset(2))),
                MinimumSupport.absolute(0.5));
        var statistics = result.statistics();

        assertEquals(5, statistics.candidateCount());
        assertEquals(1, statistics.acceptedCandidateCount());
        assertEquals(4, statistics.rejectedCandidateCount());
        assertEquals(1, statistics.iCandidateCount());
        assertEquals(4, statistics.sCandidateCount());
        assertTrue(result.prefixTree().find(rejected).isEmpty());
    }

    @Test
    void mixedExtensionsSatisfyCandidateAcceptanceAndTreeInvariants() {
        var result = miner.mine(
                database(Sequence.of(itemset(1, 2), itemset(1))),
                MinimumSupport.absolute(0.5));
        var statistics = result.statistics();

        assertTrue(statistics.iCandidateCount() > 0);
        assertTrue(statistics.sCandidateCount() > 0);
        assertEquals(statistics.candidateCount(),
                statistics.iCandidateCount() + statistics.sCandidateCount());
        assertEquals(statistics.candidateCount(),
                statistics.acceptedCandidateCount() + statistics.rejectedCandidateCount());
        assertEquals(statistics.patternCount(),
                statistics.f1Count() + statistics.acceptedCandidateCount());
        assertEquals(result.prefixTree().nodeCount() - 1, statistics.patternCount());
        assertEquals(statistics.patternCount(), statistics.expandedNodeCount());
    }

    @Test
    void deterministicCountersRepeatWhileElapsedTimeIsOnlyObserved() {
        var input = database(
                Sequence.of(itemset(1, 2), itemset(1)),
                Sequence.of(itemset(1), itemset(2)));

        var first = miner.mine(input, MinimumSupport.absolute(0.3));
        var second = miner.mine(input, MinimumSupport.absolute(0.3));

        assertDeterministicStatisticsEqual(first.statistics(), second.statistics());
        assertEquals(first.supports(), second.supports());
        assertEquals(first.acceptedPatterns(), second.acceptedPatterns());
    }

    @Test
    void consecutiveMiningRunsKeepIndependentImmutableSnapshots() {
        var first = miner.mine(
                database(Sequence.of(itemset(1), itemset(1))),
                MinimumSupport.absolute(0.5));
        WspmMiningStatistics firstSnapshot = first.statistics();

        var second = miner.mine(database(), MinimumSupport.absolute(0.0));

        assertEquals(2, firstSnapshot.patternCount());
        assertEquals(1, firstSnapshot.acceptedCandidateCount());
        assertEquals(0, second.statistics().patternCount());
        assertEquals(firstSnapshot, first.statistics());
        assertTrue(WspmMiningStatistics.class.isRecord());
        assertTrue(isFinal(WspmMiningStatistics.class.getModifiers()));
        assertTrue(Arrays.stream(WspmMiningStatistics.class.getRecordComponents())
                .allMatch(component -> component.getType().isPrimitive()));
    }

    @Test
    void immutableStatisticsRejectInternallyInconsistentSnapshots() {
        assertThrows(IllegalArgumentException.class, () -> new WspmMiningStatistics(
                1, 1, 2, 1, 0, 1, 1, 1, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new WspmMiningStatistics(
                1, 1, 1, 1, 0, 1, 0, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new WspmMiningStatistics(
                1, 1, 0, 0, 0, 0, 0, 1, 1, -1));
    }

    private static void assertDeterministicStatisticsEqual(
            WspmMiningStatistics expected, WspmMiningStatistics actual) {
        assertEquals(expected.sequenceCount(), actual.sequenceCount());
        assertEquals(expected.f1Count(), actual.f1Count());
        assertEquals(expected.candidateCount(), actual.candidateCount());
        assertEquals(expected.acceptedCandidateCount(), actual.acceptedCandidateCount());
        assertEquals(expected.rejectedCandidateCount(), actual.rejectedCandidateCount());
        assertEquals(expected.iCandidateCount(), actual.iCandidateCount());
        assertEquals(expected.sCandidateCount(), actual.sCandidateCount());
        assertEquals(expected.expandedNodeCount(), actual.expandedNodeCount());
        assertEquals(expected.patternCount(), actual.patternCount());
        assertTrue(expected.elapsedNanos() >= 0);
        assertTrue(actual.elapsedNanos() >= 0);
    }

    private static SequenceDatabase database(Sequence... sequences) {
        var records = new ArrayList<SequenceRecord>();
        for (int index = 0; index < sequences.length; index++) {
            records.add(new SequenceRecord(index + 1, sequences[index]));
        }
        return new SequenceDatabase(records);
    }

    private static Itemset itemset(int... ids) {
        return new Itemset(Arrays.stream(ids).mapToObj(Item::new).toList());
    }
}
