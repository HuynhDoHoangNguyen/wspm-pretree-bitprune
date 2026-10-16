package vn.edu.wspm.mining;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.prism.PatternEncoding;
import vn.edu.wspm.prism.PositionBlock;
import vn.edu.wspm.prism.SequenceBlock;

/** Builds the full Prime-block encodings used to seed the DFS miner from F1. */
public final class SingletonEncodingBuilder {

    /**
     * Builds one full singleton encoding per F1 item with a single database traversal.
     *
     * <p>The scan is preprocessing: later DFS support is obtained only from Prime-block joins.
     * The current PRISM representation addresses SIDs as the contiguous range {@code 1..N}, so
     * this boundary validates that database records use that same coordinate system.</p>
     */
    public Map<Integer, PatternEncoding> build(
            SequenceDatabase database, Collection<WeightedOnePattern> f1) {
        Objects.requireNonNull(database, "sequence database must not be null");
        Objects.requireNonNull(f1, "F1 must not be null");

        var orderedF1 = new TreeMap<Integer, WeightedOnePattern>();
        for (var onePattern : f1) {
            Objects.requireNonNull(onePattern, "F1 must not contain null");
            if (orderedF1.putIfAbsent(onePattern.itemId(), onePattern) != null) {
                throw new IllegalArgumentException(
                        "F1 contains duplicate item id: " + onePattern.itemId());
            }
        }
        if (orderedF1.isEmpty()) {
            return Map.of();
        }

        var positionsByItem = new TreeMap<Integer, List<PositionBlock>>();
        var supportingSidsByItem = new TreeMap<Integer, List<Integer>>();
        for (int itemId : orderedF1.keySet()) {
            positionsByItem.put(itemId, new ArrayList<>(database.size()));
            supportingSidsByItem.put(itemId, new ArrayList<>());
        }

        for (int recordIndex = 0; recordIndex < database.records().size(); recordIndex++) {
            var record = database.records().get(recordIndex);
            int expectedSid = recordIndex + 1;
            if (record.sid() != expectedSid) {
                throw new IllegalArgumentException(
                        "Prime-block encoding requires records with contiguous SIDs 1..N in order; "
                                + "expected SID " + expectedSid + " but found " + record.sid());
            }

            var positionsInSequence = new TreeMap<Integer, List<Integer>>();
            for (int itemsetIndex = 0;
                    itemsetIndex < record.sequence().itemsets().size();
                    itemsetIndex++) {
                int oneBasedPosition = itemsetIndex + 1;
                for (var item : record.sequence().itemsets().get(itemsetIndex).items()) {
                    if (orderedF1.containsKey(item.id())) {
                        positionsInSequence.computeIfAbsent(
                                item.id(), ignored -> new ArrayList<>()).add(oneBasedPosition);
                    }
                }
            }

            int sequenceSize = record.sequence().size();
            for (int itemId : orderedF1.keySet()) {
                var positions = positionsInSequence.getOrDefault(itemId, List.of());
                positionsByItem.get(itemId).add(PositionBlock.of(
                        record.sid(),
                        sequenceSize,
                        positions.stream().mapToInt(Integer::intValue).toArray()));
                if (!positions.isEmpty()) {
                    supportingSidsByItem.get(itemId).add(record.sid());
                }
            }
        }

        var result = new LinkedHashMap<Integer, PatternEncoding>();
        for (var entry : orderedF1.entrySet()) {
            int itemId = entry.getKey();
            int[] supportingSids = supportingSidsByItem.get(itemId).stream()
                    .mapToInt(Integer::intValue)
                    .toArray();
            var encoding = new PatternEncoding(
                    SequenceBlock.of(database.size(), supportingSids),
                    positionsByItem.get(itemId));
            if (encoding.support() != entry.getValue().support()) {
                throw new IllegalArgumentException(
                        "singleton encoding support disagrees with F1 for item " + itemId);
            }
            result.put(itemId, encoding);
        }
        return Collections.unmodifiableMap(result);
    }
}
