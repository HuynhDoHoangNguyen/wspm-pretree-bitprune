package vn.edu.wspm.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PatternExtensionTest {

    private static final Item ZERO = new Item(0);
    private static final Item ONE = new Item(1);
    private static final Item TWO = new Item(2);
    private static final Item THREE = new Item(3);

    private final PatternExtensions extensions = new PatternExtensions();

    @Test
    void createsDistinctIAndSExtensionsWithoutMutatingInput() {
        var original = Pattern.of(Itemset.of(ONE), Itemset.of(TWO));

        var iExtended = extensions.iExtend(original, THREE);
        var sExtended = extensions.sExtend(original, THREE);

        assertEquals("<(1)(2)>", original.serialize());
        assertEquals("<(1)(2 3)>", iExtended.serialize());
        assertEquals("<(1)(2)(3)>", sExtended.serialize());
        assertNotEquals(iExtended, sExtended);
        assertEquals(2, iExtended.size());
        assertEquals(3, sExtended.size());
        assertEquals(3, iExtended.length());
        assertEquals(3, sExtended.length());
    }

    @Test
    void allowsSExtensionButRejectsIExtensionOnEmptyPattern() {
        assertEquals("<(1)>", extensions.sExtend(Pattern.empty(), ONE).serialize());
        assertThrows(IllegalArgumentException.class,
                () -> extensions.iExtend(Pattern.empty(), ONE));
    }

    @Test
    void rejectsDuplicateAndNonIncreasingIExtensions() {
        var pattern = Pattern.of(Itemset.of(ONE, THREE));

        assertThrows(IllegalArgumentException.class, () -> extensions.iExtend(pattern, ONE));
        assertThrows(IllegalArgumentException.class, () -> extensions.iExtend(pattern, THREE));
        assertThrows(IllegalArgumentException.class, () -> extensions.iExtend(pattern, TWO));
        assertThrows(IllegalArgumentException.class, () -> extensions.iExtend(pattern, ZERO));
        assertEquals("<(1 3)>", pattern.serialize());
    }

    @Test
    void allowsRepeatedItemAcrossItemsetsThroughSExtension() {
        var original = Pattern.singleton(ONE);

        var extended = extensions.sExtend(original, ONE);

        assertEquals("<(1)>", original.serialize());
        assertEquals("<(1)(1)>", extended.serialize());
    }

    @Test
    void realizesLexicographicGateAsAscendingNumericItemOrder() {
        var two = Pattern.singleton(TWO);
        var ten = new Item(10);

        assertEquals("<(2 10)>", extensions.iExtend(two, ten).serialize());
        assertThrows(IllegalArgumentException.class,
                () -> extensions.iExtend(Pattern.singleton(ten), TWO));
    }

    @Test
    void reproducesPaperStructuralChildrenOfMappedItemA() {
        var itemA = Pattern.singleton(ONE);

        assertEquals("<(1)(1)>", extensions.sExtend(itemA, ONE).serialize());
        assertEquals("<(1)(2)>", extensions.sExtend(itemA, TWO).serialize());
        assertEquals("<(1)(3)>", extensions.sExtend(itemA, THREE).serialize());
        assertEquals("<(1 2)>", extensions.iExtend(itemA, TWO).serialize());
    }

    @Test
    void rejectsNullInputsInsteadOfReturningNullOrOriginalPattern() {
        assertThrows(NullPointerException.class, () -> extensions.iExtend(null, ONE));
        assertThrows(NullPointerException.class,
                () -> extensions.iExtend(Pattern.singleton(ONE), null));
        assertThrows(NullPointerException.class, () -> extensions.sExtend(null, ONE));
        assertThrows(NullPointerException.class,
                () -> extensions.sExtend(Pattern.singleton(ONE), null));
    }
}
