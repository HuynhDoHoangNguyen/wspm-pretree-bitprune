package vn.edu.wspm.mining;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.SpmfSequenceParser;

/** Synthetic two-sequence correctness fixture; at most 4*4*2 choices per sequence. */
class MultiPageFixtureOracleTest {
    @Test void boundedSyntheticFixtureHasRealPaginationAndRepeatedTemporalItems() throws Exception {
        var db = new SpmfSequenceParser().parse(new StringReader("1 2 -1 1 3 -1 2 -1 -2\n1 2 -1 1 3 -1 2 -1 -2\n"));
        var minimum = MinimumSupport.absolute(0.1);
        var expected = EngineE2EOracleVerifier.canonical(new BruteForceWeightedPatternOracle().mine(db, minimum));
        assertTrue(expected.size() > 20);
        assertEquals(2, expected.get("<(1)(1)>"));
        assertEquals(2, expected.get("<(1 2)>"));
        EngineE2EOracleVerifier.verify(db, minimum, expected, expected.size());
    }
}
