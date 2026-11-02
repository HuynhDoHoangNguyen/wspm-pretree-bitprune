package vn.edu.wspm.mining;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.prism.PatternEncoding;
import vn.edu.wspm.prism.PositionBlock;
import vn.edu.wspm.prism.SequenceBlock;

/** Test-only direct structural oracle; deliberately independent of production Prime-block joins. */
final class CandidateTestOracle {

    private CandidateTestOracle() {}

    static PatternEncoding encoding(SequenceDatabase database, Pattern pattern) {
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("test oracle requires a non-empty pattern");
        }
        int sidCount = database.size();
        var positionsBySid = new ArrayList<PositionBlock>(sidCount);
        var supportingSids = new ArrayList<Integer>();

        for (var record : database.records()) {
            List<Integer> endpoints = directEndpoints(record.sequence(), pattern);
            positionsBySid.add(PositionBlock.of(
                    record.sid(),
                    record.sequence().size(),
                    endpoints.stream().mapToInt(Integer::intValue).toArray()));
            if (!endpoints.isEmpty()) {
                supportingSids.add(record.sid());
            }
        }

        return new PatternEncoding(
                SequenceBlock.of(
                        sidCount,
                        supportingSids.stream().mapToInt(Integer::intValue).toArray()),
                positionsBySid);
    }

    static Map<Integer, PatternEncoding> singletonEncodings(
            SequenceDatabase database, List<WeightedOnePattern> f1) {
        var encodings = new TreeMap<Integer, PatternEncoding>();
        for (var onePattern : f1) {
            encodings.put(onePattern.itemId(), encoding(
                    database, Pattern.singleton(new Item(onePattern.itemId()))));
        }
        return Map.copyOf(encodings);
    }

    static List<Integer> supportingSids(PatternEncoding encoding) {
        var result = new ArrayList<Integer>();
        for (int sid = 1; sid <= encoding.sequenceBlock().sidCount(); sid++) {
            if (encoding.sequenceBlock().isSupporting(sid)) {
                result.add(sid);
            }
        }
        return List.copyOf(result);
    }

    static List<Integer> directSupportingSids(
            SequenceDatabase database, Pattern pattern) {
        var result = new ArrayList<Integer>();
        for (var record : database.records()) {
            if (!directEndpoints(record.sequence(), pattern).isEmpty()) {
                result.add(record.sid());
            }
        }
        return List.copyOf(result);
    }

    private static List<Integer> directEndpoints(Sequence sequence, Pattern pattern) {
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
}
