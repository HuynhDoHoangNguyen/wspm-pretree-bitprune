package vn.edu.wspm.bitprune.cmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;

class CmapSupportCountTest {
    private static final Item A = new Item(0);
    private static final Item B = new Item(2);
    private static final Item Z = new Item(Integer.MAX_VALUE);
    private final CmapBuilder builder = new CmapBuilder();

    private static SequenceRecord record(int sid, Itemset... sets) {
        return new SequenceRecord(sid, Sequence.of(sets));
    }

    @Test
    void distinctSidCountsSeparateIAndSAndDoNotCountEmbeddings() {
        var db = new SequenceDatabase(List.of(
                record(-5, Itemset.of(A), Itemset.of(B), Itemset.of(B)),
                record(0, Itemset.of(A, B)),
                record(42, Itemset.of(A), Itemset.of(B))));
        var i = builder.buildI(db, List.of(A, B, Z));
        var s = builder.buildS(db, List.of(A, B, Z));
        assertEquals(1, i.supportCount(A, B));
        assertEquals(2, s.supportCount(A, B));
        assertEquals(0, s.supportCount(B, A));
        assertEquals(1, s.supportCount(B, B));
        assertEquals(0, i.supportCount(A, Z));
        assertEquals(0, s.supportCount(Z, Z));
        assertTrue(i.contains(A, B));
        assertEquals(0, builder.buildS(new SequenceDatabase(List.of()), List.of(A, B))
                .supportCount(A, B));
    }

    @Test
    void repeatedItemsetsCountOnceButIdenticalRecordsWithDifferentSidsCountTwice() {
        var db = new SequenceDatabase(List.of(
                record(1, Itemset.of(A, B), Itemset.of(A, B), Itemset.of(A, B)),
                record(2, Itemset.of(A, B), Itemset.of(A, B), Itemset.of(A, B))));
        var i = builder.buildI(db, List.of(A, B));
        var s = builder.buildS(db, List.of(A, B));
        assertEquals(2, i.supportCount(A, B));
        for (Item a : List.of(A, B)) {
            for (Item b : List.of(A, B)) {
                assertEquals(2, s.supportCount(a, b));
            }
        }
        var reversed = new SequenceDatabase(List.of(db.records().get(1), db.records().get(0)));
        assertEquals(2, builder.buildS(reversed, List.of(B, A)).supportCount(A, B));
        var relabeled = new SequenceDatabase(List.of(
                new SequenceRecord(-99, db.records().get(0).sequence()),
                new SequenceRecord(78, db.records().get(1).sequence())));
        assertEquals(2, builder.buildI(relabeled, List.of(B, A)).supportCount(A, B));
        // A new build on the same builder must not carry counts from an earlier run.
        assertEquals(1, builder.buildI(new SequenceDatabase(List.of(db.records().get(0))),
                List.of(A, B)).supportCount(A, B));
    }

    @Test
    void sameItemsetAndCrossSidEvidenceCannotCreateTemporalCounts() {
        var db = new SequenceDatabase(List.of(record(1, Itemset.of(A, Z))));
        assertEquals(1, builder.buildI(db, List.of(A, Z)).supportCount(A, Z));
        var s = builder.buildS(db, List.of(A, Z));
        assertEquals(0, s.supportCount(A, Z));
        assertEquals(0, s.supportCount(A, A));
        db = new SequenceDatabase(List.of(record(1, Itemset.of(A)), record(2, Itemset.of(Z))));
        assertEquals(0, builder.buildI(db, List.of(A, Z)).supportCount(A, Z));
        assertEquals(0, builder.buildS(db, List.of(A, Z)).supportCount(A, Z));
    }

    @Test
    void countQueriesValidateTheSameDomainAsContains() {
        var db = new SequenceDatabase(List.of(record(1, Itemset.of(A, B, Z))));
        var i = builder.buildI(db, List.of(A, B));
        var s = builder.buildS(db, List.of(A, B));
        assertThrows(IllegalArgumentException.class, () -> i.supportCount(B, A));
        assertThrows(IllegalArgumentException.class, () -> i.supportCount(A, A));
        assertThrows(IllegalArgumentException.class, () -> i.supportCount(A, Z));
        assertThrows(IllegalArgumentException.class, () -> s.supportCount(Z, A));
        assertThrows(NullPointerException.class, () -> i.supportCount(null, B));
        assertThrows(NullPointerException.class, () -> s.supportCount(A, null));
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildI(db, List.of(A, A)));
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildS(db, List.of(A, A)));
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildS(db, List.of()).supportCount(A, B));
    }

    @Test
    void countsAreDefensivelyCopiedAndStoredCountsMustBePositive() {
        var universe = new HashSet<>(List.of(A, Z));
        var counts = new HashMap<>(Map.of(CmapI.pairKey(A, Z), 2));
        var i = new CmapI(universe, counts);
        var s = new CmapS(universe, counts);
        counts.put(CmapI.pairKey(A, Z), 99);
        universe.clear();
        assertEquals(2, i.supportCount(A, Z));
        assertEquals(2, s.supportCount(A, Z));
        assertThrows(IllegalArgumentException.class,
                () -> new CmapI(java.util.Set.of(A, Z), Map.of(CmapI.pairKey(A, Z), 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new CmapS(java.util.Set.of(A, Z), Map.of(CmapS.pairKey(A, Z), -1)));
    }
}
