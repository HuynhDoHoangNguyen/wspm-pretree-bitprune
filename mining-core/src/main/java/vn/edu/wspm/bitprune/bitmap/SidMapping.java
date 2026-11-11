package vn.edu.wspm.bitprune.bitmap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import vn.edu.wspm.model.SequenceDatabase;

/**
 * Bijective mapping between actual sequence identifiers (actual SIDs)
 * and compact bit indices in {@code [0, sequenceCount)}.
 *
 * <p>
 * Under the C02 architecture contract, mapping order is determined by the
 * sequence database record order (the {@code k}-th record has bit index
 * {@code k}).
 * SIDs may be negative, zero, positive, or noncontiguous.
 * </p>
 */
public final class SidMapping {

    private final int[] indexToSid;
    private final Map<Integer, Integer> sidToIndex;

    private SidMapping(int[] indexToSid, Map<Integer, Integer> sidToIndex) {
        this.indexToSid = indexToSid;
        this.sidToIndex = sidToIndex;
    }

    /**
     * Creates an immutable {@link SidMapping} from the given sequence database.
     *
     * @param database the sequence database (must not be null)
     * @return the bijective SID mapping
     * @throws NullPointerException if {@code database} is null
     */
    public static SidMapping of(SequenceDatabase database) {
        Objects.requireNonNull(database, "database must not be null");

        var records = database.records();
        int size = records.size();
        int[] indexToSid = new int[size];
        Map<Integer, Integer> sidToIndex = new HashMap<>(size == 0 ? 0 : (int) Math.ceil(size / 0.75f) + 1);

        for (int i = 0; i < size; i++) {
            int sid = records.get(i).sid();
            indexToSid[i] = sid;
            sidToIndex.put(sid, i);
        }

        return new SidMapping(indexToSid, Map.copyOf(sidToIndex));
    }

    /**
     * Returns the number of sequences in the mapped universe.
     *
     * @return the universe size
     */
    public int size() {
        return indexToSid.length;
    }

    /**
     * Maps an actual SID to its compact bit index in {@code [0, size())}.
     *
     * @param actualSid the actual sequence identifier
     * @return the compact bit index
     * @throws IllegalArgumentException if the SID does not belong to this universe
     */
    public int sidToIndex(int actualSid) {
        Integer index = sidToIndex.get(actualSid);
        if (index == null) {
            throw new IllegalArgumentException("SID not found in universe: " + actualSid);
        }
        return index;
    }

    /**
     * Maps a compact bit index in {@code [0, size())} back to its actual SID.
     *
     * @param bitIndex the compact bit index
     * @return the actual sequence identifier
     * @throws IndexOutOfBoundsException if {@code bitIndex < 0} or
     *                                   {@code bitIndex >= size()}
     */
    public int indexToSid(int bitIndex) {
        if (bitIndex < 0 || bitIndex >= indexToSid.length) {
            throw new IndexOutOfBoundsException(
                    "Invalid bit index: " + bitIndex + " for universe size: " + indexToSid.length);
        }
        return indexToSid[bitIndex];
    }

    /**
     * Returns a defensive copy of the actual SIDs ordered by their bit indices.
     *
     * @return array of actual SIDs
     */
    public int[] actualSids() {
        return indexToSid.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SidMapping that)) {
            return false;
        }
        return Arrays.equals(this.indexToSid, that.indexToSid);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(indexToSid);
    }

    @Override
    public String toString() {
        return "SidMapping[size=" + size() + ", sids=" + Arrays.toString(indexToSid) + "]";
    }
}
