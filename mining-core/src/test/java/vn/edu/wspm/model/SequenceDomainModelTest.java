package vn.edu.wspm.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SequenceDomainModelTest {

    private static final Item ONE = new Item(1);
    private static final Item TWO = new Item(2);
    private static final Item THREE = new Item(3);

    @Test
    void keepsItemsetBoundaries() {
        var twoItemsets = Sequence.of(Itemset.of(ONE), Itemset.of(TWO));
        var oneItemset = Sequence.of(Itemset.of(ONE, TWO));

        assertNotEquals(twoItemsets, oneItemset);
        assertEquals(2, twoItemsets.size());
        assertEquals(1, oneItemset.size());
        assertEquals(2, twoItemsets.length());
        assertEquals(2, oneItemset.length());
    }

    @Test
    void preservesTemporalOrderBetweenItemsets() {
        var oneThenTwo = Sequence.of(Itemset.of(ONE), Itemset.of(TWO));
        var twoThenOne = Sequence.of(Itemset.of(TWO), Itemset.of(ONE));

        assertNotEquals(oneThenTwo, twoThenOne);
        assertEquals(List.of(Itemset.of(ONE), Itemset.of(TWO)), oneThenTwo.itemsets());
    }

    @Test
    void rejectsDuplicateItemWithinOneItemset() {
        var error = assertThrows(IllegalArgumentException.class, () -> Itemset.of(ONE, ONE));

        assertTrue(error.getMessage().contains("duplicate item"));
    }

    @Test
    void preservesRepeatedItemAcrossDifferentItemsets() {
        var sequence = Sequence.of(Itemset.of(ONE), Itemset.of(TWO), Itemset.of(ONE, THREE));

        assertEquals(3, sequence.size());
        assertEquals(4, sequence.length());
        assertTrue(sequence.itemsets().get(0).contains(ONE));
        assertTrue(sequence.itemsets().get(2).contains(ONE));
    }

    @Test
    void canonicalizesUnorderedItemsetsDeterministically() {
        var reverseInput = Itemset.of(THREE, ONE, TWO);

        assertEquals(List.of(ONE, TWO, THREE), reverseInput.items());
        assertEquals(Itemset.of(ONE, TWO, THREE), reverseInput);
    }

    @Test
    void ordersIdentifiersNumericallyRatherThanLexically() {
        var two = new Item(2);
        var ten = new Item(10);

        assertEquals(List.of(two, ten), Itemset.of(ten, two).items());
    }

    @Test
    void rejectsNegativeIdentifiersAndAllowsZero() {
        assertThrows(IllegalArgumentException.class, () -> new Item(-1));
        assertThrows(IllegalArgumentException.class, () -> new Item(-2));
        assertEquals(0, new Item(0).id());
    }

    @Test
    void defensivelyCopiesMutableInputCollections() {
        var mutableItemsets = new ArrayList<>(List.of(Itemset.of(ONE)));
        var sequence = new Sequence(mutableItemsets);
        mutableItemsets.add(Itemset.of(TWO));

        assertEquals(1, sequence.size());
        assertThrows(UnsupportedOperationException.class,
                () -> sequence.itemsets().add(Itemset.of(THREE)));
    }

    @Test
    void sequenceDatabasePreservesRecordOrderAndRejectsDuplicateSids() {
        var first = new SequenceRecord(10, Sequence.of(Itemset.of(ONE)));
        var second = new SequenceRecord(20, Sequence.of(Itemset.of(TWO)));
        var database = new SequenceDatabase(List.of(first, second));

        assertEquals(List.of(first, second), database.records());
        assertEquals(2, database.size());
        assertThrows(IllegalArgumentException.class,
                () -> new SequenceDatabase(List.of(first,
                        new SequenceRecord(10, Sequence.of(Itemset.of(THREE))))));
    }
}
