package vn.edu.wspm.bitprune.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.mining.WeightedOnePattern;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.tree.ExtensionType;

/**
 * Structural unit tests for {@link BitPruneCandidateEngine} and
 * {@link BitPruneCandidateResult}.
 */
class BitPruneCandidateEngineTest {

        private BitPruneCandidateEngine engine;

        @BeforeEach
        void setUp() {
                engine = new BitPruneCandidateEngine();
        }

        @Test
        void singletonParentEnumeratesCanonicalIThenAllS() {
                var parent = Pattern.of(Itemset.of(new Item(1)));
                var f1 = List.of(
                                new WeightedOnePattern(1, 10),
                                new WeightedOnePattern(2, 20),
                                new WeightedOnePattern(3, 30));

                var results = engine.generateCandidates(parent, f1);

                assertEquals(5, results.size());

                // Eligible I-Extensions first (x > 1): items 2 and 3
                assertEquals(
                                new BitPruneCandidateResult(
                                                Pattern.of(Itemset.of(new Item(1), new Item(2))),
                                                ExtensionType.I_EXTENSION,
                                                new Item(2)),
                                results.get(0));
                assertEquals(
                                new BitPruneCandidateResult(
                                                Pattern.of(Itemset.of(new Item(1), new Item(3))),
                                                ExtensionType.I_EXTENSION,
                                                new Item(3)),
                                results.get(1));

                // All S-Extensions second: items 1, 2, 3
                assertEquals(
                                new BitPruneCandidateResult(
                                                Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(1))),
                                                ExtensionType.S_EXTENSION,
                                                new Item(1)),
                                results.get(2));
                assertEquals(
                                new BitPruneCandidateResult(
                                                Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2))),
                                                ExtensionType.S_EXTENSION,
                                                new Item(2)),
                                results.get(3));
                assertEquals(
                                new BitPruneCandidateResult(
                                                Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(3))),
                                                ExtensionType.S_EXTENSION,
                                                new Item(3)),
                                results.get(4));

                assertEquals("<(1 2)>", results.get(0).pattern().serialize());
                assertEquals("<(1 3)>", results.get(1).pattern().serialize());
                assertEquals("<(1)(1)>", results.get(2).pattern().serialize());
                assertEquals("<(1)(2)>", results.get(3).pattern().serialize());
                assertEquals("<(1)(3)>", results.get(4).pattern().serialize());
        }

        @Test
        void multiItemFinalItemsetUsesGreatestLastItem() {
                var parent = Pattern.of(Itemset.of(new Item(1), new Item(3)));
                var f1 = List.of(
                                new WeightedOnePattern(1, 10),
                                new WeightedOnePattern(2, 20),
                                new WeightedOnePattern(3, 30),
                                new WeightedOnePattern(4, 40),
                                new WeightedOnePattern(5, 50));

                var results = engine.generateCandidates(parent, f1);

                // Greatest last item in final itemset is 3
                // I-extensions are strictly for items > 3 (4 and 5)
                var iExtensions = results.stream()
                                .filter(r -> r.extensionType() == ExtensionType.I_EXTENSION)
                                .toList();
                assertEquals(2, iExtensions.size());
                assertEquals(new Item(4), iExtensions.get(0).extensionItem());
                assertEquals("<(1 3 4)>", iExtensions.get(0).pattern().serialize());
                assertEquals(new Item(5), iExtensions.get(1).extensionItem());
                assertEquals("<(1 3 5)>", iExtensions.get(1).pattern().serialize());

                // Items 1, 2, 3 must be excluded from I-extensions
                for (var iExt : iExtensions) {
                        assertNotEquals(new Item(1), iExt.extensionItem());
                        assertNotEquals(new Item(2), iExt.extensionItem());
                        assertNotEquals(new Item(3), iExt.extensionItem());
                }
        }

        @Test
        void multiItemsetParentPreservesBoundaries() {
                var parent = Pattern.of(
                                Itemset.of(new Item(1)),
                                Itemset.of(new Item(2), new Item(3)));
                var f1 = List.of(
                                new WeightedOnePattern(1, 10),
                                new WeightedOnePattern(2, 20),
                                new WeightedOnePattern(4, 40));

                var results = engine.generateCandidates(parent, f1);

                // I-Extension: last item is 3, eligible item > 3 is 4
                var iExtensions = results.stream()
                                .filter(r -> r.extensionType() == ExtensionType.I_EXTENSION)
                                .toList();
                assertEquals(1, iExtensions.size());
                var iChild = iExtensions.getFirst().pattern();
                assertEquals(2, iChild.size());
                assertEquals(Itemset.of(new Item(1)), iChild.itemsets().get(0));
                assertEquals(Itemset.of(new Item(2), new Item(3), new Item(4)), iChild.itemsets().get(1));
                assertEquals("<(1)(2 3 4)>", iChild.serialize());

                // S-Extensions: all F1 items append a new singleton itemset
                var sExtensions = results.stream()
                                .filter(r -> r.extensionType() == ExtensionType.S_EXTENSION)
                                .toList();
                assertEquals(3, sExtensions.size());
                for (var sExt : sExtensions) {
                        var sChild = sExt.pattern();
                        assertEquals(3, sChild.size());
                        assertEquals(Itemset.of(new Item(1)), sChild.itemsets().get(0));
                        assertEquals(Itemset.of(new Item(2), new Item(3)), sChild.itemsets().get(1));
                        assertEquals(1, sChild.itemsets().get(2).size());
                        assertEquals(sExt.extensionItem(), sChild.itemsets().get(2).items().getFirst());
                }
                assertEquals("<(1)(2 3)(1)>", sExtensions.get(0).pattern().serialize());
                assertEquals("<(1)(2 3)(2)>", sExtensions.get(1).pattern().serialize());
                assertEquals("<(1)(2 3)(4)>", sExtensions.get(2).pattern().serialize());
        }

        @Test
        void iExtensionExcludesEqualAndSmallerItems() {
                var parent = Pattern.of(Itemset.of(new Item(3)));
                var f1 = List.of(
                                new WeightedOnePattern(1, 10),
                                new WeightedOnePattern(2, 20),
                                new WeightedOnePattern(3, 30),
                                new WeightedOnePattern(4, 40));

                var results = engine.generateCandidates(parent, f1);

                var iExtensions = results.stream()
                                .filter(r -> r.extensionType() == ExtensionType.I_EXTENSION)
                                .toList();

                assertEquals(1, iExtensions.size());
                assertEquals(new Item(4), iExtensions.getFirst().extensionItem());
                assertEquals("<(3 4)>", iExtensions.getFirst().pattern().serialize());
        }

        @Test
        void sExtensionIncludesSmallerEqualAndGreaterItems() {
                var parent = Pattern.of(Itemset.of(new Item(3)));
                var f1 = List.of(
                                new WeightedOnePattern(1, 10),
                                new WeightedOnePattern(2, 20),
                                new WeightedOnePattern(3, 30),
                                new WeightedOnePattern(4, 40));

                var results = engine.generateCandidates(parent, f1);

                var sExtensions = results.stream()
                                .filter(r -> r.extensionType() == ExtensionType.S_EXTENSION)
                                .toList();

                assertEquals(4, sExtensions.size());
                assertEquals(new Item(1), sExtensions.get(0).extensionItem());
                assertEquals("<(3)(1)>", sExtensions.get(0).pattern().serialize());
                assertEquals(new Item(2), sExtensions.get(1).extensionItem());
                assertEquals("<(3)(2)>", sExtensions.get(1).pattern().serialize());
                assertEquals(new Item(3), sExtensions.get(2).extensionItem());
                assertEquals("<(3)(3)>", sExtensions.get(2).pattern().serialize());
                assertEquals(new Item(4), sExtensions.get(3).extensionItem());
                assertEquals("<(3)(4)>", sExtensions.get(3).pattern().serialize());
        }

        @Test
        void sExtensionAllowsRepeatedItemsAcrossItemsets() {
                var parent = Pattern.of(
                                Itemset.of(new Item(1)),
                                Itemset.of(new Item(2)));
                var f1 = List.of(
                                new WeightedOnePattern(1, 10),
                                new WeightedOnePattern(2, 20));

                var results = engine.generateCandidates(parent, f1);

                var sExtensions = results.stream()
                                .filter(r -> r.extensionType() == ExtensionType.S_EXTENSION)
                                .toList();

                assertEquals(2, sExtensions.size());
                assertEquals("<(1)(2)(1)>", sExtensions.get(0).pattern().serialize());
                assertEquals("<(1)(2)(2)>", sExtensions.get(1).pattern().serialize());
        }

        @Test
        void itemsetAndSequenceExtensionsHaveDifferentIdentities() {
                var parent = Pattern.of(Itemset.of(new Item(1)));
                var f1 = List.of(new WeightedOnePattern(2, 10));

                var results = engine.generateCandidates(parent, f1);

                assertEquals(2, results.size());
                var iResult = results.get(0);
                var sResult = results.get(1);

                assertEquals(ExtensionType.I_EXTENSION, iResult.extensionType());
                assertEquals(ExtensionType.S_EXTENSION, sResult.extensionType());

                assertNotEquals(iResult.pattern(), sResult.pattern());
                assertNotEquals(iResult.pattern().serialize(), sResult.pattern().serialize());
                assertEquals("<(1 2)>", iResult.pattern().serialize());
                assertEquals("<(1)(2)>", sResult.pattern().serialize());
                assertEquals(1, iResult.pattern().size());
                assertEquals(2, sResult.pattern().size());
        }

        @Test
        void emptyF1ReturnsImmutableEmptyList() {
                var parent = Pattern.of(Itemset.of(new Item(1)));
                var results = engine.generateCandidates(parent, List.of());

                assertTrue(results.isEmpty());
                assertThrows(UnsupportedOperationException.class, () -> results.add(
                                new BitPruneCandidateResult(
                                                Pattern.of(Itemset.of(new Item(1))),
                                                ExtensionType.S_EXTENSION,
                                                new Item(1))));
        }

        @Test
        void inputF1AndParentAreNotMutated() {
                var parent = Pattern.of(Itemset.of(new Item(1)));
                var f1 = new ArrayList<WeightedOnePattern>();
                f1.add(new WeightedOnePattern(3, 30));
                f1.add(new WeightedOnePattern(1, 10));
                f1.add(new WeightedOnePattern(2, 20));

                var f1Snapshot = List.copyOf(f1);
                var parentSerialized = parent.serialize();

                engine.generateCandidates(parent, f1);

                assertEquals(f1Snapshot, f1);
                assertEquals(parentSerialized, parent.serialize());
        }

        @Test
        void unsortedF1StillProducesCanonicalOrder() {
                var parent = Pattern.of(Itemset.of(new Item(1)));
                var unsortedF1 = List.of(
                                new WeightedOnePattern(3, 30),
                                new WeightedOnePattern(1, 10),
                                new WeightedOnePattern(2, 20));

                var results = engine.generateCandidates(parent, unsortedF1);

                assertEquals(5, results.size());
                // I-extensions in ascending item order (2, then 3)
                assertEquals("<(1 2)>", results.get(0).pattern().serialize());
                assertEquals("<(1 3)>", results.get(1).pattern().serialize());
                // S-extensions in ascending item order (1, then 2, then 3)
                assertEquals("<(1)(1)>", results.get(2).pattern().serialize());
                assertEquals("<(1)(2)>", results.get(3).pattern().serialize());
                assertEquals("<(1)(3)>", results.get(4).pattern().serialize());
        }

        @Test
        void duplicateF1ItemFailsFast() {
                var parent = Pattern.of(Itemset.of(new Item(1)));
                var duplicateF1 = List.of(
                                new WeightedOnePattern(2, 10),
                                new WeightedOnePattern(2, 20));

                var ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> engine.generateCandidates(parent, duplicateF1));
                assertTrue(ex.getMessage().contains("duplicate item id"));
        }

        @Test
        void nullInputsAndNullElementsFailFast() {
                var parent = Pattern.of(Itemset.of(new Item(1)));
                var validF1 = List.of(new WeightedOnePattern(1, 10));

                assertThrows(
                                NullPointerException.class,
                                () -> engine.generateCandidates(null, validF1));
                assertThrows(
                                NullPointerException.class,
                                () -> engine.generateCandidates(parent, null));

                var f1WithNull = new ArrayList<WeightedOnePattern>();
                f1WithNull.add(new WeightedOnePattern(1, 10));
                f1WithNull.add(null);
                assertThrows(
                                NullPointerException.class,
                                () -> engine.generateCandidates(parent, f1WithNull));
        }

        @Test
        void emptyParentFailsFast() {
                var emptyParent = Pattern.empty();
                var f1 = List.of(new WeightedOnePattern(1, 10));

                var ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> engine.generateCandidates(emptyParent, f1));
                assertTrue(ex.getMessage().contains("parent pattern must not be empty"));
        }

        @Test
        void candidateResultsAreImmutable() {
                var parent = Pattern.of(Itemset.of(new Item(1)));
                var f1 = List.of(new WeightedOnePattern(2, 20));

                var results = engine.generateCandidates(parent, f1);
                assertFalse(results.isEmpty());
                assertThrows(
                                UnsupportedOperationException.class,
                                () -> results.add(results.getFirst()));

                // Also verify BitPruneCandidateResult constructor null validations
                assertThrows(
                                NullPointerException.class,
                                () -> new BitPruneCandidateResult(null, ExtensionType.I_EXTENSION, new Item(1)));
                assertThrows(
                                NullPointerException.class,
                                () -> new BitPruneCandidateResult(parent, null, new Item(1)));
                assertThrows(
                                NullPointerException.class,
                                () -> new BitPruneCandidateResult(parent, ExtensionType.I_EXTENSION, null));
        }

        @Test
        void everyResultAddsExactlyOneItem() {
                var parent = Pattern.of(
                                Itemset.of(new Item(1), new Item(2)),
                                Itemset.of(new Item(3)));
                int parentLength = parent.length();
                assertEquals(3, parentLength);

                var f1 = List.of(
                                new WeightedOnePattern(1, 10),
                                new WeightedOnePattern(2, 20),
                                new WeightedOnePattern(3, 30),
                                new WeightedOnePattern(4, 40));

                var results = engine.generateCandidates(parent, f1);
                assertFalse(results.isEmpty());
                for (var result : results) {
                        assertEquals(
                                        parentLength + 1,
                                        result.pattern().length(),
                                        "Candidate must add exactly one item: " + result.pattern().serialize());
                        assertNotNull(result.extensionType());
                        assertNotNull(result.extensionItem());
                }
        }

        @Test
        void noDuplicateCandidateIdentity() {
                var parent = Pattern.of(Itemset.of(new Item(2)));
                var f1 = List.of(
                                new WeightedOnePattern(1, 10),
                                new WeightedOnePattern(2, 20),
                                new WeightedOnePattern(3, 30),
                                new WeightedOnePattern(4, 40));

                var results = engine.generateCandidates(parent, f1);

                var uniquePatterns = new HashSet<Pattern>();
                for (var result : results) {
                        assertTrue(
                                        uniquePatterns.add(result.pattern()),
                                        "Duplicate candidate pattern identity detected: "
                                                        + result.pattern().serialize());
                }
                assertEquals(uniquePatterns.size(), results.size());
        }
}
