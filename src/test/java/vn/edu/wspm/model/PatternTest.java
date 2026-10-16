package vn.edu.wspm.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.mining.WeightedOnePattern;

class PatternTest {

    private static final Item ONE = new Item(1);
    private static final Item TWO = new Item(2);
    private static final Item THREE = new Item(3);
    private static final Item FOUR = new Item(4);

    @Test
    void preservesItemsetBoundariesAndTemporalOrderInIdentity() {
        var oneThenTwo = Pattern.of(Itemset.of(ONE), Itemset.of(TWO));
        var oneAndTwo = Pattern.of(Itemset.of(ONE, TWO));
        var twoThenOne = Pattern.of(Itemset.of(TWO), Itemset.of(ONE));

        assertNotEquals(oneThenTwo, oneAndTwo);
        assertNotEquals(oneThenTwo, twoThenOne);
        assertEquals("<(1)(2)>", oneThenTwo.serialize());
        assertEquals("<(1 2)>", oneAndTwo.serialize());
        assertEquals("<(2)(1)>", twoThenOne.serialize());
    }

    @Test
    void usesStructuralEqualityAndStableHashing() {
        var first = Pattern.of(Itemset.of(ONE), Itemset.of(TWO, THREE));
        var equalCopy = new Pattern(List.of(Itemset.of(ONE), Itemset.of(THREE, TWO)));
        var set = new HashSet<>(List.of(first, equalCopy));
        var map = new HashMap<Pattern, String>();
        map.put(first, "value");

        assertEquals(first, equalCopy);
        assertEquals(first.hashCode(), equalCopy.hashCode());
        assertEquals(1, set.size());
        assertEquals("value", map.get(equalCopy));
    }

    @Test
    void representsEmptyPatternWithoutNullAccessors() {
        var empty = Pattern.empty();

        assertTrue(empty.isEmpty());
        assertEquals(0, empty.size());
        assertEquals(0, empty.length());
        assertEquals("<>", empty.serialize());
        assertEquals("<>", empty.toString());
        assertTrue(empty.lastItemset().isEmpty());
        assertTrue(empty.lastItem().isEmpty());
        assertEquals(empty, Pattern.of());
    }

    @Test
    void distinguishesSizeLengthAndProvidesLastAccessors() {
        var pattern = Pattern.of(
                Itemset.of(ONE),
                Itemset.of(TWO, THREE),
                Itemset.of(FOUR));

        assertFalse(pattern.isEmpty());
        assertEquals(3, pattern.size());
        assertEquals(4, pattern.length());
        assertEquals(Itemset.of(FOUR), pattern.lastItemset().orElseThrow());
        assertEquals(FOUR, pattern.lastItem().orElseThrow());
        assertEquals("<(1)(2 3)(4)>", pattern.serialize());
    }

    @Test
    void allowsRepeatedItemsAcrossTemporalItemsets() {
        var pattern = Pattern.of(Itemset.of(ONE), Itemset.of(ONE), Itemset.of(ONE));

        assertEquals(3, pattern.size());
        assertEquals(3, pattern.length());
        assertEquals("<(1)(1)(1)>", pattern.serialize());
    }

    @Test
    void reusesCanonicalNumericItemsetOrderingForSerialization() {
        var pattern = Pattern.of(Itemset.of(new Item(10), TWO));

        assertEquals("<(2 10)>", pattern.serialize());
        assertEquals(new Item(10), pattern.lastItem().orElseThrow());
    }

    @Test
    void defensivelyCopiesInputAndExposesImmutableStructure() {
        var mutableItemsets = new ArrayList<>(List.of(Itemset.of(ONE)));
        var pattern = new Pattern(mutableItemsets);
        mutableItemsets.add(Itemset.of(TWO));

        assertEquals("<(1)>", pattern.serialize());
        assertThrows(UnsupportedOperationException.class,
                () -> pattern.itemsets().add(Itemset.of(THREE)));
        assertThrows(UnsupportedOperationException.class,
                () -> pattern.itemsets().getFirst().items().add(TWO));
    }

    @Test
    void keepsSupportMetadataOutsidePatternIdentity() {
        var supportSeven = new WeightedOnePattern(1, 7);
        var supportFive = new WeightedOnePattern(1, 5);
        var fromSupportSeven = Pattern.singleton(new Item(supportSeven.itemId()));
        var fromSupportFive = Pattern.singleton(new Item(supportFive.itemId()));

        assertNotEquals(supportSeven, supportFive);
        assertEquals(fromSupportSeven, fromSupportFive);
        assertEquals("<(1)>", fromSupportSeven.serialize());
    }

    @Test
    void rejectsMalformedInputsThroughExistingDomainInvariants() {
        assertThrows(NullPointerException.class, () -> new Pattern(null));
        assertThrows(NullPointerException.class,
                () -> new Pattern(Arrays.asList(Itemset.of(ONE), null)));
        assertThrows(NullPointerException.class, () -> Pattern.of((Itemset[]) null));
        assertThrows(NullPointerException.class, () -> Pattern.singleton(null));
        assertThrows(IllegalArgumentException.class, () -> Itemset.of(ONE, ONE));
        assertThrows(IllegalArgumentException.class, () -> Pattern.singleton(new Item(-1)));
    }
}
