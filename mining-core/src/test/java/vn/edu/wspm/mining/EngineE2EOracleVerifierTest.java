package vn.edu.wspm.mining;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.SpmfSequenceParser;

class EngineE2EOracleVerifierTest {
    @Test void exactSupportAndStructuralIdentityAreMandatoryEvenWhenCountsAgree() {
        var expected = Map.of("<(1 2)>", 2, "<(1)(2)>", 1);
        EngineE2EOracleVerifier.exact(expected, expected, 2);
        assertThrows(AssertionError.class, () -> EngineE2EOracleVerifier.exact(expected, Map.of("<(1 2)>", 1, "<(1)(2)>", 1), 2));
        assertThrows(AssertionError.class, () -> EngineE2EOracleVerifier.exact(expected, Map.of("<(1 2)>", 2, "<(2)(1)>", 1), 2));
        assertThrows(AssertionError.class, () -> EngineE2EOracleVerifier.exact(expected, Map.of("<(1 2)>", 2), 2));
        assertThrows(AssertionError.class, () -> EngineE2EOracleVerifier.exact(expected, expected, 1));
    }
    @Test void thresholdOneUlpIsRejected() {
        EngineE2EOracleVerifier.bits(0.1, Long.toHexString(Double.doubleToRawLongBits(0.1)));
        assertThrows(AssertionError.class, () -> EngineE2EOracleVerifier.bits(0.1, Long.toHexString(Double.doubleToRawLongBits(Math.nextUp(0.1)))));
    }
    @Test void existingOracleChecksBothDirectEnginesAndEmptySuccess() throws Exception {
        var db = new SpmfSequenceParser().parse(new StringReader("1 2 -1 3 -1 1 -1 -2\n1 2 -1 -2\n1 -1 2 -1 -2\n"));
        var minimum = MinimumSupport.absolute(0.1);
        var observed = EngineE2EOracleVerifier.canonical(new BruteForceWeightedPatternOracle().mine(db, minimum));
        assertTrue(observed.containsKey("<(1 2)>")); assertTrue(observed.containsKey("<(1)(2)>"));
        EngineE2EOracleVerifier.verify(db, minimum, observed, observed.size());
        EngineE2EOracleVerifier.verify(db, MinimumSupport.percentage(100), Map.of(), 0);
        assertThrows(IllegalArgumentException.class, () -> EngineE2EOracleVerifier.verify(db, MinimumSupport.absolute(0), Map.of(), 0));
    }
}
