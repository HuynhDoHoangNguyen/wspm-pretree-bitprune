import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import tools.jackson.databind.ObjectMapper;
import vn.edu.wspm.config.*;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.mining.EngineE2EOracleVerifier;
import vn.edu.wspm.statistics.*;

/** Test-only adapter for pre-return captures. Uses packaged core and accepted independent oracle. */
public final class FinalResultE2EVerifier {
    static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static String sha(Path file) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))); }
    static Map<?,?> object(Object o) { return (Map<?,?>)o; }
    public static void main(String[] args) throws Exception {
        var p = new Properties(); try (var in = Files.newInputStream(Path.of(args[0]))) { p.load(in); }
        var mapPath = Path.of(args[1]); var input = Path.of(args[2]); var publication = Path.of(args[3]);
        var json = new ObjectMapper(); var binding = json.readValue(Files.readString(Path.of(args[4])), Map.class);
        var request = object(binding.get("request")); var config = object(binding.get("configuration"));
        require(binding.get("runId").equals(config.get("runId")), "Run identity mismatch");
        require(binding.get("snapshotId").equals(request.get("snapshotId")), "Snapshot binding mismatch");
        require(binding.get("call").toString().equals(p.getProperty("call")), "Call binding mismatch");
        require(request.get("algorithm").equals(p.getProperty("algorithm")), "Algorithm binding mismatch");
        require(request.get("minsupMode").equals(p.getProperty("mode")), "Mode binding mismatch");
        require(binding.get("sequenceSha256").equals(sha(publication)), "Publication hash mismatch");
        require(sha(input).equals(p.getProperty("input.sha256")), "Input hash mismatch");
        require(sha(mapPath).equals(p.getProperty("map.sha256")), "Map hash mismatch");
        var parser = new SpmfSequenceParser(); var db = parser.parse(input);
        require(db.records().equals(parser.parse(publication).records()), "Actual structural input mismatch");
        require(Integer.toString(db.size()).equals(p.getProperty("sequenceCount")), "Sequence count mismatch");
        double value = ((Number)request.get("minsupValue")).doubleValue();
        var minimum = new MinimumSupport(value, MinimumSupportMode.valueOf(request.get("minsupMode").toString()));
        var resolved = new MinimumSupportResolver().resolve(minimum, db.size());
        EngineE2EOracleVerifier.bits(value, p.getProperty("configured.bits"));
        EngineE2EOracleVerifier.bits(resolved.effectiveThreshold(), p.getProperty("resolved.bits"));
        EngineE2EOracleVerifier.bits(value, Long.toHexString(Double.doubleToRawLongBits(((Number)config.get("configuredValue")).doubleValue())));
        EngineE2EOracleVerifier.bits(resolved.effectiveThreshold(), Long.toHexString(Double.doubleToRawLongBits(((Number)config.get("effectiveThreshold")).doubleValue())));
        if (resolved.relativeMinimumSupport().isPresent()) EngineE2EOracleVerifier.bits(resolved.relativeMinimumSupport().getAsDouble(), p.getProperty("relative.bits"));
        else require("null".equals(p.getProperty("relative.bits")), "Unexpected relative threshold");
        var statistics = new ItemStatisticsCalculator().calculate(db);
        var weights = new ItemWeightCalculator().calculate(statistics);
        EngineE2EOracleVerifier.bits(weights.meanWeight().orElseThrow(), p.getProperty("mean.bits"));
        require(request.get("minsupMode").equals(config.get("minsupMode")), "Stored mode mismatch");
        for (var entry : Map.of("meanW", weights.meanWeight().orElseThrow(), "maxW", weights.maxWeight().orElseThrow(), "minW", weights.minWeight().orElseThrow()).entrySet())
            EngineE2EOracleVerifier.bits(entry.getValue(), Long.toHexString(Double.doubleToRawLongBits(((Number)config.get(entry.getKey())).doubleValue())));
        if (resolved.relativeMinimumSupport().isPresent())
            EngineE2EOracleVerifier.bits(resolved.relativeMinimumSupport().getAsDouble(), Long.toHexString(Double.doubleToRawLongBits(((Number)config.get("relativeFraction")).doubleValue())));
        else require(config.get("relativeFraction") == null, "Stored relative threshold mismatch");
        var map = new TreeMap<String,Integer>();
        for (String line : Files.readAllLines(mapPath)) {
            var fields = line.split("\t", -1);
            require(fields.length == 2 && map.put(fields[0], Integer.valueOf(fields[1])) == null, "Duplicate/invalid pattern row");
        }
        EngineE2EOracleVerifier.verify(db, minimum, map, Long.parseLong(p.getProperty("map.count")));
        var items = new ArrayList<Map<String,Object>>();
        statistics.itemStatistics().forEach((id, s) -> items.add(Map.of("itemId",id,"occurrenceCount",Long.toString(s.occurrenceCount()),
                "sequenceSupport",Integer.toString(s.sequenceSupport()),"itemWeight",weights.weightFor(id).orElseThrow())));
        Files.writeString(Path.of(args[5]), json.writeValueAsString(Map.of("patterns",map,"runItems",items)));
        System.out.println("PASS original=direct-PreTree=direct-BitPrune=independent-oracle; run=" + binding.get("runId") + " count=" + map.size() + "; raw-double/input/call binding verified");
    }
}
