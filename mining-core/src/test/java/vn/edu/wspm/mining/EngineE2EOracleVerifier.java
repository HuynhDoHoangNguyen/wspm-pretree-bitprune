package vn.edu.wspm.mining;

import java.util.Map;
import java.util.TreeMap;
import vn.edu.wspm.bitprune.mining.BitPruneMiner;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.SequenceDatabase;

/** Test-runtime bridge to the existing independent tiny-database oracle. Never packaged. */
public final class EngineE2EOracleVerifier {
    private EngineE2EOracleVerifier() {}
    public static Map<String, Integer> canonical(Map<Pattern, Integer> source) {
        var map = new TreeMap<String, Integer>();
        source.forEach((pattern, support) -> {
            if (map.put(pattern.serialize(), support) != null) throw new AssertionError("Duplicate identity");
        });
        return map;
    }
    public static void exact(Map<String, Integer> expected, Map<String, Integer> actual, long count) {
        if (actual.size() != count || !expected.equals(actual)) {
            var keys = new java.util.TreeSet<>(expected.keySet()); keys.addAll(actual.keySet());
            for (var key : keys) if (!java.util.Objects.equals(expected.get(key), actual.get(key)))
                throw new AssertionError("First pattern/support mismatch: " + key);
            throw new AssertionError("Pattern count mismatch");
        }
    }
    public static void bits(double expected, String actual) {
        if (!Long.toUnsignedString(Double.doubleToRawLongBits(expected), 16).equals(actual))
            throw new AssertionError("Threshold/metadata raw-bit mismatch");
    }
    public static void verify(SequenceDatabase database, MinimumSupport minimum,
            Map<String, Integer> observed, long count) {
        if (!(new MinimumSupportResolver().resolve(minimum, database.size()).effectiveThreshold() > 0.0))
            throw new IllegalArgumentException("R6 remains open");
        var oracle = canonical(new BruteForceWeightedPatternOracle().mine(database, minimum));
        exact(oracle, canonical(new WspmPreTreeMiner().mine(database, minimum).supports()), oracle.size());
        exact(oracle, canonical(new BitPruneMiner().mine(database, minimum).supports()), oracle.size());
        exact(oracle, observed, count);
    }
}
