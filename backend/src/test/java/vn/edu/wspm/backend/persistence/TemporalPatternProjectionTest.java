package vn.edu.wspm.backend.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import vn.edu.wspm.backend.services.run.persistence.*;
import vn.edu.wspm.backend.services.run.persistence.TemporalPatternProjector.ItemRow;
import vn.edu.wspm.model.*;

/** Synthetic structural/producer fixtures only; no claim of mining completeness. */
class TemporalPatternProjectionTest {
    static final Itemset ONE = Itemset.of(new Item(1));
    static final Itemset TWO = Itemset.of(new Item(2));
    static final Pattern REPEATED = Pattern.of(Itemset.of(new Item(1), new Item(2)), ONE);

    static PatternPersistenceProjector.Batch parents(Map<Pattern, Integer> supports) throws Exception {
        return PatternPersistenceProjector.project(PatternPersistenceProjectionTest.context(PatternPersistenceProjectionTest.input(), supports));
    }
    static List<ItemRow> validRows() { return List.of(new ItemRow(1, 1, 1), new ItemRow(1, 2, 2), new ItemRow(2, 1, 1)); }

    @Test void exactOrdersAndRepeatedItemsRoundTripWithoutFlattening() throws Exception {
        var parent = parents(Map.of(REPEATED, 1)); var batch = TemporalPatternProjector.project(parent);
        assertEquals(3, batch.rowCount()); assertEquals(validRows(), batch.patterns().getFirst().items());
        assertEquals(REPEATED, TemporalPatternProjector.reconstruct(parent.rows().getFirst(), validRows(), parent.context().products()));
        assertThrows(UnsupportedOperationException.class, () -> batch.patterns().clear());
        assertThrows(UnsupportedOperationException.class, () -> batch.patterns().getFirst().items().clear());
    }

    @Test void distinctTemporalIdentitiesAndOrderingRemainDeterministic() throws Exception {
        var together = Pattern.of(Itemset.of(new Item(1), new Item(2)));
        var sequence = Pattern.of(ONE, TWO); var reverse = Pattern.of(TWO, ONE);
        var supports = new LinkedHashMap<Pattern, Integer>(); supports.put(together, 1); supports.put(sequence, 1); supports.put(reverse, 1);
        var batch = TemporalPatternProjector.project(parents(supports));
        var flipped = new LinkedHashMap<Pattern, Integer>(); flipped.put(reverse, 1); flipped.put(sequence, 1); flipped.put(together, 1);
        assertEquals(batch.patterns(), TemporalPatternProjector.project(parents(flipped)).patterns());
        assertEquals(3, batch.patterns().stream().map(p -> p.parent().hash()).distinct().count());
        for (var projected : batch.patterns()) {
            assertEquals(projected.parent().canonical(), TemporalPatternProjector.reconstruct(projected.parent(), projected.items(),
                    batch.parents().context().products()).serialize());
        }
    }

    static Stream<List<ItemRow>> malformedRows() {
        return Stream.of(
                List.of(),
                List.of(new ItemRow(1, 1, 1)),
                List.of(new ItemRow(2, 1, 1), new ItemRow(2, 2, 2), new ItemRow(3, 1, 1)),
                List.of(new ItemRow(1, 1, 1), new ItemRow(1, 3, 2), new ItemRow(2, 1, 1)),
                List.of(new ItemRow(1, 1, 1), new ItemRow(1, 2, 2), new ItemRow(3, 1, 1)),
                List.of(new ItemRow(1, 1, 2), new ItemRow(1, 2, 1), new ItemRow(2, 1, 1)),
                List.of(new ItemRow(1, 1, 1), new ItemRow(1, 2, 1), new ItemRow(2, 1, 1)),
                List.of(new ItemRow(1, 1, 1), new ItemRow(1, 2, 2), new ItemRow(2, 2, 1)),
                List.of(new ItemRow(1, 0, 1), new ItemRow(1, 2, 2), new ItemRow(2, 1, 1)),
                List.of(new ItemRow(0, 1, 1), new ItemRow(1, 2, 2), new ItemRow(2, 1, 1)),
                List.of(new ItemRow(1, 1, 0), new ItemRow(1, 2, 2), new ItemRow(2, 1, 1)),
                List.of(new ItemRow(1, 1, 1), new ItemRow(1, 2, 99), new ItemRow(2, 1, 1)),
                List.of(new ItemRow(1, 1, 1), new ItemRow(2, 1, 2), new ItemRow(1, 2, 1)));
    }

    @ParameterizedTest @MethodSource("malformedRows") void malformedRawRowsAreRejectedBeforeCanonicalization(List<ItemRow> rows) throws Exception {
        var parent = parents(Map.of(REPEATED, 1));
        assertThrows(IllegalArgumentException.class, () -> TemporalPatternProjector.reconstruct(parent.rows().getFirst(), rows, parent.context().products()));
    }

    @Test void alteredParentHashCountsOrCanonicalAndWrongDictionaryAreRejected() throws Exception {
        var batch = parents(Map.of(REPEATED, 1)); var row = batch.rows().getFirst();
        for (var changed : List.of(
                new PatternPersistenceProjector.Row("<(1)(2)(1)>", row.hash(), 1, 1, .25, 3, 3, .25, row.policy()),
                new PatternPersistenceProjector.Row(row.canonical(), "f".repeat(64), 1, 1, .25, 3, 2, .25, row.policy()),
                new PatternPersistenceProjector.Row(row.canonical(), row.hash(), 1, 1, .25, 2, 2, .25, row.policy()),
                new PatternPersistenceProjector.Row(row.canonical(), row.hash(), 1, 1, .25, 3, 1, .25, row.policy()))) {
            assertThrows(IllegalArgumentException.class, () -> TemporalPatternProjector.reconstruct(changed, validRows(), batch.context().products()));
        }
        assertThrows(IllegalArgumentException.class, () -> TemporalPatternProjector.reconstruct(row, validRows(), Map.of(1, "p1")));
    }

    @Test void temporalRowLimitAcceptsBoundaryAndRejectsWholeOversizeBatch() throws Exception {
        var supports = new LinkedHashMap<Pattern, Integer>();
        for (int i = 0; i < 5; i++) {
            var sets = new ArrayList<>(Collections.nCopies(20000, ONE));
            for (int bit = 0; bit < 3; bit++) if ((i & (1 << bit)) != 0) sets.set(bit, TWO);
            supports.put(new Pattern(sets), 1);
        }
        var batch = TemporalPatternProjector.project(parents(supports));
        assertEquals(100000, batch.rowCount());
        supports.put(Pattern.of(ONE), 1);
        var parentOnly = parents(supports); // Still valid inside the accepted 06.3 limits.
        assertThrows(IllegalArgumentException.class, () -> TemporalPatternProjector.project(parentOnly));
    }

    @Test void emptyResultHasNoTemporalRowsAndNoCompletionMarker() throws Exception {
        var batch = TemporalPatternProjector.project(parents(Map.of()));
        assertTrue(batch.patterns().isEmpty()); assertEquals(0, batch.rowCount());
    }
}
