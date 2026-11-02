package vn.edu.wspm.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.SequenceDatabaseFormat;
import vn.edu.wspm.io.SequenceDatabaseParser;
import vn.edu.wspm.model.SequenceDatabase;

class ResultRetentionPolicyTest {

    private final WspmPreTreeMiner miner = new WspmPreTreeMiner();
    private final SequenceDatabaseParser parser = new SequenceDatabaseParser();

    @Test
    void tableOneRetentionModeEquality() {
        var database = loadDatabase("/datasets/wspm/table1.spmf", SequenceDatabaseFormat.SPMF_SEQUENTIAL);
        var minsup = MinimumSupport.percentage(14.0);

        var fullResult = miner.mine(database, minsup, ResultRetentionPolicy.FULL_ENCODING);
        var supportOnlyResult = miner.mine(database, minsup, ResultRetentionPolicy.SUPPORT_ONLY);

        // Pattern-support equality
        assertEquals(fullResult.supports(), supportOnlyResult.supports());
        assertEquals(fullResult.acceptedPatterns(), supportOnlyResult.acceptedPatterns());
        assertEquals(9, supportOnlyResult.supports().size());

        // Statistics equality
        var fullStats = fullResult.statistics();
        var suppStats = supportOnlyResult.statistics();
        assertEquals(fullStats.candidateCount(), suppStats.candidateCount());
        assertEquals(fullStats.acceptedCandidateCount(), suppStats.acceptedCandidateCount());
        assertEquals(fullStats.rejectedCandidateCount(), suppStats.rejectedCandidateCount());
        assertEquals(fullStats.iCandidateCount(), suppStats.iCandidateCount());
        assertEquals(fullStats.sCandidateCount(), suppStats.sCandidateCount());
        assertEquals(fullStats.expandedNodeCount(), suppStats.expandedNodeCount());
        assertEquals(fullStats.patternCount(), suppStats.patternCount());
        assertEquals(fullStats.f1Count(), suppStats.f1Count());
        assertEquals(fullStats.sequenceCount(), suppStats.sequenceCount());

        // PrefixTree structural equality
        assertEquals(
                fullResult.prefixTree().preOrderPatterns(),
                supportOnlyResult.prefixTree().preOrderPatterns());
        assertEquals(
                fullResult.prefixTree().nodeCount(),
                supportOnlyResult.prefixTree().nodeCount());

        // FULL_ENCODING capabilities
        assertTrue(fullResult.hasEncodings());
        assertEquals(ResultRetentionPolicy.FULL_ENCODING, fullResult.retentionPolicy());
        assertEquals(9, fullResult.encodings().size());
        for (var pattern : fullResult.acceptedPatterns()) {
            assertEquals(
                    fullResult.support(pattern),
                    fullResult.encoding(pattern).sequenceBlock().support());
        }

        // SUPPORT_ONLY capabilities & explicit absence contract
        assertFalse(supportOnlyResult.hasEncodings());
        assertEquals(ResultRetentionPolicy.SUPPORT_ONLY, supportOnlyResult.retentionPolicy());
        assertThrows(UnsupportedOperationException.class, supportOnlyResult::encodings);
        for (var pattern : supportOnlyResult.acceptedPatterns()) {
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> supportOnlyResult.encoding(pattern));
            assertEquals(fullResult.support(pattern), supportOnlyResult.support(pattern));
        }
    }

    @Test
    void signPointFourRetentionModeEquality() {
        var database = loadDatabase("/datasets/sign.txt", SequenceDatabaseFormat.SPMF_SEQUENTIAL);
        var minsup = MinimumSupport.percentage(0.4);

        var fullResult = miner.mine(database, minsup, ResultRetentionPolicy.FULL_ENCODING);
        var supportOnlyResult = miner.mine(database, minsup, ResultRetentionPolicy.SUPPORT_ONLY);

        assertEquals(315, fullResult.acceptedPatterns().size());
        assertEquals(315, supportOnlyResult.acceptedPatterns().size());
        assertEquals(18_198L, fullResult.statistics().candidateCount());
        assertEquals(18_198L, supportOnlyResult.statistics().candidateCount());

        assertEquals(fullResult.supports(), supportOnlyResult.supports());
        assertEquals(fullResult.acceptedPatterns(), supportOnlyResult.acceptedPatterns());
        assertEquals(
                fullResult.prefixTree().preOrderPatterns(),
                supportOnlyResult.prefixTree().preOrderPatterns());
    }

    @Test
    void pumsbPointSixtySixRetentionModeEquality() {
        var database = loadDatabase("/datasets/pumsb.txt", SequenceDatabaseFormat.FIMI_TRANSACTION);
        var minsup = MinimumSupport.percentage(0.66);

        var fullResult = miner.mine(database, minsup, ResultRetentionPolicy.FULL_ENCODING);
        var supportOnlyResult = miner.mine(database, minsup, ResultRetentionPolicy.SUPPORT_ONLY);

        assertEquals(15, fullResult.acceptedPatterns().size());
        assertEquals(15, supportOnlyResult.acceptedPatterns().size());
        assertEquals(113L, fullResult.statistics().candidateCount());
        assertEquals(113L, supportOnlyResult.statistics().candidateCount());

        assertEquals(fullResult.supports(), supportOnlyResult.supports());
        assertEquals(fullResult.acceptedPatterns(), supportOnlyResult.acceptedPatterns());
        assertEquals(
                fullResult.prefixTree().preOrderPatterns(),
                supportOnlyResult.prefixTree().preOrderPatterns());
    }

    @Test
    void nullRetentionPolicyRejectedFast() {
        var database = loadDatabase("/datasets/wspm/table1.spmf", SequenceDatabaseFormat.SPMF_SEQUENTIAL);
        var minsup = MinimumSupport.percentage(14.0);

        assertThrows(
                NullPointerException.class,
                () -> miner.mine(database, minsup, null));
    }

    private SequenceDatabase loadDatabase(String resourcePath, SequenceDatabaseFormat format) {
        var url = getClass().getResource(resourcePath);
        org.junit.jupiter.api.Assumptions.assumeTrue(
                url != null, "research dataset not found on classpath: " + resourcePath);
        try {
            return parser.parse(Path.of(url.toURI()), format);
        } catch (Exception exception) {
            throw new RuntimeException("failed to load resource: " + resourcePath, exception);
        }
    }
}
