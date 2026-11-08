import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import vn.edu.wspm.config.*;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.mining.EngineE2EOracleVerifier;
import vn.edu.wspm.statistics.ItemStatisticsCalculator;
import vn.edu.wspm.statistics.ItemWeightCalculator;

/** Verifies evidence exported from the ORIGINAL retained result; direct miners are comparators. */
public final class EngineRunVerifier {
    static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static String sha(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    public static void main(String[] args) throws Exception {
        var p = new Properties(); try (var in = Files.newInputStream(Path.of(args[0]))) { p.load(in); }
        var mapPath = Path.of(args[1]); var sequence = Path.of(args[2]); var mapping = Path.of(args[3]); var actualInput = Path.of(args[4]);
        String id = args[5], algorithm = args[6], mode = args[7]; double value = Double.parseDouble(args[8]);
        require(id.equals(p.getProperty("db.run_id")) && algorithm.equals(p.getProperty("db.algorithm")), "Run identity mismatch");
        require("SUCCEEDED".equals(p.getProperty("db.status")) && "true".equals(p.getProperty("hasResult")) && "true".equals(p.getProperty("lifecycleConfirmed")), "Original success required");
        require(!"null".equals(p.getProperty("db.execution_attempt_id")) && !"null".equals(p.getProperty("db.execution_owner_id")), "Owned attempt required");
        require(mode.equals(p.getProperty("db.minsup_mode")), "Mode mismatch");
        require(sha(sequence).equals(p.getProperty("db.sequence_sha256")) && sha(mapping).equals(p.getProperty("db.mapping_sha256")), "Published artifact hash mismatch");
        require(sha(mapPath).equals(p.getProperty("map.sha256")), "Original map digest mismatch");
        var parser = new SpmfSequenceParser(); var db = parser.parse(sequence); var input = parser.parse(actualInput);
        require(db.records().equals(input.records()), "Actual engine input structure differs from publication");
        require(db.size() == Integer.parseInt(p.getProperty("sequenceCount")), "Sequence count mismatch");
        var minimum = new MinimumSupport(value, MinimumSupportMode.valueOf(mode));
        var weights = new ItemWeightCalculator().calculate(new ItemStatisticsCalculator().calculate(db));
        EngineE2EOracleVerifier.bits(weights.meanWeight().orElseThrow(), p.getProperty("mean.bits"));
        for (var entry : Map.of("mean_w", weights.meanWeight().orElseThrow(), "max_w", weights.maxWeight().orElseThrow(), "min_w", weights.minWeight().orElseThrow()).entrySet())
            EngineE2EOracleVerifier.bits(entry.getValue(), Long.toHexString(Double.doubleToRawLongBits(Double.parseDouble(p.getProperty("db." + entry.getKey())))));
        var resolved = new MinimumSupportResolver().resolve(minimum, db.size());
        EngineE2EOracleVerifier.bits(value, p.getProperty("configured.bits"));
        EngineE2EOracleVerifier.bits(resolved.effectiveThreshold(), p.getProperty("resolved.bits"));
        EngineE2EOracleVerifier.bits(value, Long.toHexString(Double.doubleToRawLongBits(Double.parseDouble(p.getProperty("db.minsup_configured")))));
        EngineE2EOracleVerifier.bits(resolved.effectiveThreshold(), Long.toHexString(Double.doubleToRawLongBits(Double.parseDouble(p.getProperty("db.effective_threshold")))));
        if (resolved.relativeMinimumSupport().isPresent()) EngineE2EOracleVerifier.bits(resolved.relativeMinimumSupport().getAsDouble(), p.getProperty("relative.bits"));
        else require("null".equals(p.getProperty("relative.bits")), "Absolute mode has no relative fraction");
        var observed = new TreeMap<String, Integer>();
        for (var line : Files.readAllLines(mapPath)) { var fields = line.split("\t", -1); require(fields.length == 2 && observed.put(fields[0], Integer.parseInt(fields[1])) == null, "Invalid/duplicate map row"); }
        long count = Long.parseLong(p.getProperty("map.count"));
        EngineE2EOracleVerifier.verify(db, minimum, observed, count);
        require(Long.toString(count).equals(p.getProperty("db.pattern_count")), "DB count mismatch");
        require(p.getProperty("native.candidates").equals(p.getProperty("db.candidate_count")), "Native candidate count mismatch");
        for (var pair : Map.of("capture.dataset", "dataset_io_ms", "native.preprocessing", "preprocessing_ms", "native.dfs", "dfs_mining_ms", "native.complete", "complete_algorithm_ms", "capture.total", "total_execution_ms").entrySet()) {
            long raw = Long.parseLong(p.getProperty(pair.getKey())); require(raw >= 0 && Long.toString(raw / 1_000_000).equals(p.getProperty("db." + pair.getValue())), "Independent ns/ms floor mismatch");
        }
        require(p.getProperty("native.heap").equals(p.getProperty("db.peak_sampled_jvm_heap_bytes")), "JVM heap sample mismatch");
        require("null".equals(p.getProperty("db.output_io_ms")), "No output scope was executed");
        System.out.println("PASS original run=" + id + " algorithm=" + algorithm + " patterns=" + count + " full-map=oracle=PreTree=BitPrune; native-metrics=DB");
    }
}
