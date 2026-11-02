package vn.edu.wspm.mining;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.prism.PatternEncoding;
import vn.edu.wspm.prism.PositionCoordinateSystem;
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

        int[] sequenceSizes = new int[database.size()];
        var compactByItem = new TreeMap<Integer, CompactAccumulator>();
        for (int itemId : orderedF1.keySet()) {
            compactByItem.put(itemId, new CompactAccumulator());
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
            sequenceSizes[recordIndex] = sequenceSize;
            for (var positionsEntry : positionsInSequence.entrySet()) {
                compactByItem.get(positionsEntry.getKey()).add(
                        record.sid(), positionsEntry.getValue());
            }
        }

        var coordinates = PositionCoordinateSystem.of(sequenceSizes);
        var result = new LinkedHashMap<Integer, PatternEncoding>();
        for (var entry : orderedF1.entrySet()) {
            int itemId = entry.getKey();
            var compact = compactByItem.get(itemId);
            int[] supportingSids = compact.sids.toArray();
            var encoding = PatternEncoding.fromCompact(
                    SequenceBlock.of(database.size(), supportingSids),
                    coordinates,
                    supportingSids,
                    compact.offsets.toArray(),
                    compact.blockIndexes.toArray(),
                    compact.ranks.toArray());
            if (encoding.support() != entry.getValue().support()) {
                throw new IllegalArgumentException(
                        "singleton encoding support disagrees with F1 for item " + itemId);
            }
            result.put(itemId, encoding);
        }
        return Collections.unmodifiableMap(result);
    }

    private static final class CompactAccumulator {
        private final IntArrayBuilder sids = new IntArrayBuilder();
        private final IntArrayBuilder offsets = new IntArrayBuilder();
        private final IntArrayBuilder blockIndexes = new IntArrayBuilder();
        private final IntArrayBuilder ranks = new IntArrayBuilder();

        private CompactAccumulator() {
            offsets.add(0);
        }

        private void add(int sid, List<Integer> positions) {
            if (positions.isEmpty()) return;
            sids.add(sid);
            int currentBlock = -1;
            int currentRank = 0;
            for (int position : positions) {
                int bitIndex = position - 1;
                int block = bitIndex / vn.edu.wspm.prism.PrimalBlockCodec.BLOCK_WIDTH;
                if (block != currentBlock) {
                    if (currentBlock >= 0) {
                        blockIndexes.add(currentBlock);
                        ranks.add(currentRank);
                    }
                    currentBlock = block;
                    currentRank = 0;
                }
                currentRank |= 1 << (bitIndex % vn.edu.wspm.prism.PrimalBlockCodec.BLOCK_WIDTH);
            }
            blockIndexes.add(currentBlock);
            ranks.add(currentRank);
            offsets.add(ranks.size());
        }
    }

    private static final class IntArrayBuilder {
        private int[] values = new int[8];
        private int size;

        private void add(int value) {
            if (size == values.length) {
                int capacity = Math.addExact(values.length, values.length >>> 1);
                values = Arrays.copyOf(values, Math.max(capacity, Math.addExact(size, 1)));
            }
            values[size++] = value;
        }

        private int size() {
            return size;
        }

        private int[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }
}
