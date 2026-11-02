package vn.edu.wspm.bitprune.index;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import vn.edu.wspm.bitprune.bitmap.SidMapping;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;

/**
 * An immutable vertical occurrence index for a sequence database.
 *
 * <p>
 * For each item {@code x} and each actual SID {@code s} in the database:
 *
 * <pre>
 *   Index(x, s) = { p | 1 &lt;= p &lt;= sequenceSize(s) and x belongs to itemset s[p] }
 * </pre>
 *
 * The stored array for each item/SID pair is the <em>exact ascending</em>
 * representation of that
 * set: 1-based itemset positions, strictly increasing, deterministic.
 *
 * <p>
 * <strong>SOURCE-CONFIRMED:</strong>
 *
 * <ul>
 * <li>The vertical occurrence model (item → actual SID → itemset positions) is
 * the standard
 * positional index required for exact temporal verification in WSPM_BitPrune,
 * as described
 * in the C02 architecture contract and motivated by OBL-07 (all feasible
 * endpoints must be
 * preserved).
 * <li>Positions are 1-based: the first itemset in a sequence has position 1
 * (source: WSPM
 * literature convention for itemset temporal ordering; confirmed consistent
 * with Phase B
 * position semantics).
 * <li>Repeated occurrences of an item across different itemsets must be
 * preserved (source:
 * C01.1 semantic contract §7.3 — multiple embeddings; C01.2 OBL-07).
 * <li>Positions must be sorted in strictly increasing temporal order (source:
 * temporal ordering
 * invariant of the sequence model).
 * <li>The index must be immutable after construction to allow sharing across
 * DFS recursion
 * without defensive copying at every level (source: C02 architecture contract
 * §6.2
 * ownership and lifecycle).
 * </ul>
 *
 * <p>
 * <strong>IMPLEMENTATION POLICY / REASONABLE INFERENCE:</strong>
 *
 * <ul>
 * <li>The minimum query API {@code positions(Item, int)} accepts an <em>actual
 * SID</em> (not a
 * compact bit index). This distinction is critical: actual SIDs may be
 * negative, zero,
 * positive, or noncontiguous. Using a bit index as an actual SID would silently
 * corrupt
 * results. This API shape is not directly specified in the 2023 or 2026 papers,
 * but is
 * the only semantically safe design given the established {@link SidMapping}
 * contract.
 * <li>When a valid actual SID is present in the mapping but the item has no
 * occurrence in that
 * sequence, {@code positions()} returns {@code new int[0]}. This is the natural
 * representation
 * of an empty set and avoids a separate "not found" exception path for a
 * semantically valid
 * absence.
 * <li>An unknown actual SID (not present in the mapping) is rejected with
 * {@link IllegalArgumentException}. This matches
 * {@link SidMapping#sidToIndex(int)} behaviour
 * and prevents silent misuse of bit indices or arbitrary integers as SIDs.
 * <li>The mapping/database consistency check (same SID set, same record order)
 * is performed at
 * build time to ensure that the compact bit indices implied by the mapping
 * agree with the
 * record traversal order used to build the index. The check compares actual SID
 * arrays in
 * order, which is equivalent to requiring that
 * {@code SidMapping.of(database).equals(mapping)}.
 * <li>Output arrays are always defensively copied so that caller mutations
 * cannot corrupt the
 * stored state. Input is similarly isolated during construction.
 * </ul>
 */
public final class VerticalOccurrenceIndex {

    /**
     * Internal storage: item → (actualSid → sorted 1-based position array).
     *
     * <p>
     * Only items that appear at least once in the database have an entry. For items
     * that are
     * present, the inner map contains an entry for every SID in which they appear;
     * SIDs where the
     * item is absent are simply not present in the inner map (absent-item queries
     * are handled by
     * returning {@code new int[0]} in {@link #positions}).
     *
     * <p>
     * Both the outer and inner maps are unmodifiable views created with
     * {@link Collections#unmodifiableMap}. The arrays stored as values are final
     * copies and are
     * never mutated after construction.
     */
    private final Map<Item, Map<Integer, int[]>> index;

    /**
     * The set of actual SIDs known to this index, used for unknown-SID detection.
     */
    private final HashSet<Integer> knownSids;

    private VerticalOccurrenceIndex(Map<Item, Map<Integer, int[]>> index, HashSet<Integer> knownSids) {
        this.index = index;
        this.knownSids = knownSids;
    }

    /**
     * Builds an immutable {@link VerticalOccurrenceIndex} from the given sequence
     * database and SID
     * mapping.
     *
     * <p>
     * The method scans the database in the order provided by
     * {@link SequenceDatabase#records()},
     * iterating through each sequence's itemsets in temporal order (position 1, 2,
     * …,
     * {@code sequence.size()}). Every item occurrence is recorded at its exact
     * 1-based position.
     * Repeated occurrences of the same item in different itemsets of the same
     * sequence are all
     * recorded.
     *
     * @param database the sequence database (must not be null)
     * @param mapping  the SID mapping (must not be null; must agree with
     *                 {@code database} in both
     *                 SID set and record order)
     * @return an immutable vertical occurrence index
     * @throws NullPointerException     if {@code database} or {@code mapping} is
     *                                  null
     * @throws IllegalArgumentException if {@code mapping} does not match
     *                                  {@code database} in size,
     *                                  actual SID set, or record order
     */
    public static VerticalOccurrenceIndex build(SequenceDatabase database, SidMapping mapping) {
        Objects.requireNonNull(database, "database must not be null");
        Objects.requireNonNull(mapping, "mapping must not be null");

        validateConsistency(database, mapping);

        // Mutable builder map: item -> (actualSid -> growing list of positions)
        // We use ArrayList<Integer> during construction, then convert to int[] for
        // immutability.
        Map<Item, Map<Integer, List<Integer>>> builder = new HashMap<>();

        List<SequenceRecord> records = database.records();
        for (SequenceRecord record : records) {
            int actualSid = record.sid();
            var itemsets = record.sequence().itemsets();
            int seqSize = itemsets.size();

            for (int pos = 1; pos <= seqSize; pos++) {
                var itemset = itemsets.get(pos - 1); // 0-based list access for 1-based position
                for (Item item : itemset.items()) {
                    builder
                            .computeIfAbsent(item, k -> new HashMap<>())
                            .computeIfAbsent(actualSid, k -> new ArrayList<>())
                            .add(pos);
                }
            }
        }

        // Convert to immutable structure: int[] per item/SID, positions are already
        // ascending
        // because we iterated pos from 1 to seqSize in order.
        Map<Item, Map<Integer, int[]>> frozen = new HashMap<>(builder.size());
        for (var entry : builder.entrySet()) {
            Item item = entry.getKey();
            Map<Integer, List<Integer>> sidMap = entry.getValue();
            Map<Integer, int[]> frozenSidMap = new HashMap<>(sidMap.size());
            for (var sidEntry : sidMap.entrySet()) {
                List<Integer> posList = sidEntry.getValue();
                int[] posArray = posList.stream().mapToInt(Integer::intValue).toArray();
                // positions are strictly increasing by construction (pos increments 1..seqSize)
                frozenSidMap.put(sidEntry.getKey(), posArray);
            }
            frozen.put(item, Collections.unmodifiableMap(frozenSidMap));
        }

        // Build the set of known actual SIDs for unknown-SID detection at query time
        HashSet<Integer> knownSids = new HashSet<>(records.size() * 2);
        for (SequenceRecord record : records) {
            knownSids.add(record.sid());
        }

        return new VerticalOccurrenceIndex(Collections.unmodifiableMap(frozen), knownSids);
    }

    /**
     * Returns the sorted 1-based itemset positions at which {@code item} appears in
     * the sequence
     * identified by {@code actualSid}.
     *
     * <p>
     * <strong>IMPLEMENTATION POLICY / REASONABLE INFERENCE:</strong> This method
     * accepts an
     * <em>actual SID</em>, not a compact bit index. Callers must not pass a bit
     * index obtained from
     * {@link SidMapping#sidToIndex} where an actual SID is required.
     *
     * @param item      the item to look up (must not be null)
     * @param actualSid the actual sequence identifier as stored in the database
     * @return a defensive copy of the sorted 1-based position array; never null;
     *         empty if the item
     *         does not appear in the sequence
     * @throws NullPointerException     if {@code item} is null
     * @throws IllegalArgumentException if {@code actualSid} is not a known SID in
     *                                  this index's
     *                                  universe
     */
    public int[] positions(Item item, int actualSid) {
        Objects.requireNonNull(item, "item must not be null");

        if (!knownSids.contains(actualSid)) {
            throw new IllegalArgumentException(
                    "Unknown actual SID: " + actualSid + " — not present in this index's universe");
        }

        Map<Integer, int[]> sidMap = index.get(item);
        if (sidMap == null) {
            // Item never appears anywhere in the database
            return new int[0];
        }

        int[] stored = sidMap.get(actualSid);
        if (stored == null) {
            // Item appears in the database but not in this particular SID
            return new int[0];
        }

        // Always return a defensive copy
        return stored.clone();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Validates that the mapping and database agree in size, SID set and record
     * order.
     *
     * <p>
     * The check compares actual SID arrays element-by-element in record order.
     * This is equivalent to requiring
     * {@code SidMapping.of(database).equals(mapping)}.
     *
     * @throws IllegalArgumentException if any inconsistency is detected
     */
    private static void validateConsistency(SequenceDatabase database, SidMapping mapping) {
        List<SequenceRecord> records = database.records();
        int dbSize = records.size();
        int mappingSize = mapping.size();

        if (dbSize != mappingSize) {
            throw new IllegalArgumentException(
                    "SidMapping size (" + mappingSize + ") does not match database size (" + dbSize + ")");
        }

        // Compare actual SID at each position in record order
        int[] mappingActualSids = mapping.actualSids(); // defensive copy from SidMapping
        for (int i = 0; i < dbSize; i++) {
            int dbSid = records.get(i).sid();
            int mappingSid = mappingActualSids[i];
            if (dbSid != mappingSid) {
                throw new IllegalArgumentException(
                        "SidMapping record order mismatch at position "
                                + i
                                + ": database SID="
                                + dbSid
                                + ", mapping SID="
                                + mappingSid);
            }
        }
    }
}
