package vn.edu.wspm.bitprune.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.bitprune.statistics.BitPruneStatistics;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.tree.ExtensionType;
import vn.edu.wspm.tree.PrefixTree;

/**
 * Unit and boundary contract tests for {@link BitPruneMiningResult} (C12.3).
 */
class BitPruneMiningResultTest {

        private final Item item1 = new Item(1);
        private final Item item2 = new Item(2);
        private final Pattern p1 = Pattern.singleton(item1);
        private final Pattern p2 = Pattern.singleton(item2);
        private final Pattern p12I = Pattern.of(Itemset.of(item1, item2));

        private PrefixTree createSampleTree() {
                PrefixTree tree = new PrefixTree();
                tree.insert(Pattern.empty(), p1, ExtensionType.S_EXTENSION);
                tree.insert(Pattern.empty(), p2, ExtensionType.S_EXTENSION);
                tree.insert(p1, p12I, ExtensionType.I_EXTENSION);
                tree.freeze();
                return tree;
        }

        private BitPruneStatistics createSampleStatistics(
                        long sequenceCount,
                        long f1Count,
                        long generatedExtensions,
                        long generatedIExtensions,
                        long generatedSExtensions,
                        long cmapRejected,
                        long bitmapRejected,
                        long exactRejected,
                        long acceptedExtensions,
                        long expandedNodes,
                        long patternCount,
                        long exactVerifications) {
                return new BitPruneStatistics(
                                sequenceCount,
                                f1Count,
                                generatedExtensions,
                                generatedIExtensions,
                                generatedSExtensions,
                                cmapRejected,
                                bitmapRejected,
                                exactRejected,
                                acceptedExtensions,
                                expandedNodes,
                                patternCount,
                                exactVerifications);
        }

        @Test
        @DisplayName("class is final and maintains immutable boundary")
        void testClassFinalAndImmutableBoundary() {
                assertTrue(Modifier.isFinal(BitPruneMiningResult.class.getModifiers()),
                                "BitPruneMiningResult class must be final");

                for (Field field : BitPruneMiningResult.class.getDeclaredFields()) {
                        assertTrue(Modifier.isPrivate(field.getModifiers()),
                                        "Field " + field.getName() + " must be private");
                        assertTrue(Modifier.isFinal(field.getModifiers()),
                                        "Field " + field.getName() + " must be final");
                }
        }

        @Test
        @DisplayName("constructor rejects null arguments")
        void testConstructorRejectsNullArguments() {
                PrefixTree tree = createSampleTree();
                Map<Pattern, Integer> supports = Map.of(p1, 2, p2, 3, p12I, 2);
                BitPruneStatistics stats = createSampleStatistics(3, 2, 1, 1, 0, 0, 0, 0, 1, 3, 3, 1);

                assertThrows(NullPointerException.class,
                                () -> new BitPruneMiningResult(null, supports, stats),
                                "null prefixTree must be rejected");

                assertThrows(NullPointerException.class,
                                () -> new BitPruneMiningResult(tree, null, stats),
                                "null supports must be rejected");

                assertThrows(NullPointerException.class,
                                () -> new BitPruneMiningResult(tree, supports, null),
                                "null statistics must be rejected");
        }

        @Test
        @DisplayName("tree must be frozen")
        void testTreeMustBeFrozen() {
                PrefixTree unfrozenTree = new PrefixTree();
                unfrozenTree.insert(Pattern.empty(), p1, ExtensionType.S_EXTENSION);
                // Not frozen!

                Map<Pattern, Integer> supports = Map.of(p1, 2);
                BitPruneStatistics stats = createSampleStatistics(2, 1, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0);

                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                () -> new BitPruneMiningResult(unfrozenTree, supports, stats));
                assertTrue(ex.getMessage().contains("frozen"));
        }

        @Test
        @DisplayName("supports are defensively copied and input map mutation does not affect result")
        void testSupportsDefensivelyCopiedAndMutationImmune() {
                PrefixTree tree = createSampleTree();
                Map<Pattern, Integer> mutableSupports = new LinkedHashMap<>();
                mutableSupports.put(p1, 2);
                mutableSupports.put(p2, 3);
                mutableSupports.put(p12I, 2);

                BitPruneStatistics stats = createSampleStatistics(3, 2, 1, 1, 0, 0, 0, 0, 1, 3, 3, 1);
                BitPruneMiningResult result = new BitPruneMiningResult(tree, mutableSupports, stats);

                assertEquals(3, result.supports().size());
                assertEquals(2, result.support(p1));

                // Mutate original map
                mutableSupports.put(p1, 999);
                mutableSupports.clear();

                assertEquals(3, result.supports().size(), "result supports must not be cleared");
                assertEquals(2, result.support(p1), "result support value must not be mutated");
        }

        @Test
        @DisplayName("supports map is unmodifiable")
        void testSupportsMapIsUnmodifiable() {
                PrefixTree tree = createSampleTree();
                Map<Pattern, Integer> supports = Map.of(p1, 2, p2, 3, p12I, 2);
                BitPruneStatistics stats = createSampleStatistics(3, 2, 1, 1, 0, 0, 0, 0, 1, 3, 3, 1);

                BitPruneMiningResult result = new BitPruneMiningResult(tree, supports, stats);

                assertThrows(UnsupportedOperationException.class,
                                () -> result.supports().put(p1, 10));
                assertThrows(UnsupportedOperationException.class,
                                () -> result.supports().clear());
                assertThrows(UnsupportedOperationException.class,
                                () -> result.supports().remove(p1));
        }

        @Test
        @DisplayName("accepted patterns is an immutable list in tree pre-order")
        void testAcceptedPatternsImmutablePreOrderList() {
                PrefixTree tree = createSampleTree();
                Map<Pattern, Integer> supports = Map.of(p1, 2, p2, 3, p12I, 2);
                BitPruneStatistics stats = createSampleStatistics(3, 2, 1, 1, 0, 0, 0, 0, 1, 3, 3, 1);

                BitPruneMiningResult result = new BitPruneMiningResult(tree, supports, stats);
                List<Pattern> patterns = result.acceptedPatterns();

                assertEquals(3, patterns.size());
                assertEquals(List.of(p1, p12I, p2), patterns,
                                "Pre-order traversal of sample tree should be p1, p12I, p2");

                assertThrows(UnsupportedOperationException.class,
                                () -> patterns.add(p1));
                assertThrows(UnsupportedOperationException.class,
                                () -> patterns.clear());
        }

        @Test
        @DisplayName("support lookup succeeds for accepted patterns and fails for unknown patterns")
        void testSupportLookup() {
                PrefixTree tree = createSampleTree();
                Map<Pattern, Integer> supports = Map.of(p1, 2, p2, 3, p12I, 2);
                BitPruneStatistics stats = createSampleStatistics(3, 2, 1, 1, 0, 0, 0, 0, 1, 3, 3, 1);

                BitPruneMiningResult result = new BitPruneMiningResult(tree, supports, stats);

                assertEquals(2, result.support(p1));
                assertEquals(3, result.support(p2));
                assertEquals(2, result.support(p12I));

                Pattern unknownPattern = Pattern.singleton(new Item(999));
                assertThrows(IllegalArgumentException.class,
                                () -> result.support(unknownPattern));

                assertThrows(NullPointerException.class,
                                () -> result.support(null));
        }

        @Test
        @DisplayName("support key mismatch is rejected")
        void testSupportKeyMismatchRejected() {
                PrefixTree tree = createSampleTree();
                BitPruneStatistics stats = createSampleStatistics(3, 2, 1, 1, 0, 0, 0, 0, 1, 3, 3, 1);

                // Missing p12I
                Map<Pattern, Integer> missingKey = Map.of(p1, 2, p2, 3);
                assertThrows(IllegalArgumentException.class,
                                () -> new BitPruneMiningResult(tree, missingKey, stats));

                // Extra key not in tree
                Pattern extra = Pattern.singleton(new Item(99));
                Map<Pattern, Integer> extraKey = Map.of(p1, 2, p2, 3, p12I, 2, extra, 1);
                assertThrows(IllegalArgumentException.class,
                                () -> new BitPruneMiningResult(tree, extraKey, stats));
        }

        @Test
        @DisplayName("null and negative support values are rejected")
        void testNullAndNegativeSupportValuesRejected() {
                PrefixTree tree = createSampleTree();
                BitPruneStatistics stats = createSampleStatistics(3, 2, 1, 1, 0, 0, 0, 0, 1, 3, 3, 1);

                // Null value
                Map<Pattern, Integer> nullValueMap = new HashMap<>();
                nullValueMap.put(p1, 2);
                nullValueMap.put(p2, null);
                nullValueMap.put(p12I, 2);
                assertThrows(NullPointerException.class,
                                () -> new BitPruneMiningResult(tree, nullValueMap, stats));

                // Negative value
                Map<Pattern, Integer> negativeValueMap = new LinkedHashMap<>();
                negativeValueMap.put(p1, 2);
                negativeValueMap.put(p2, -1);
                negativeValueMap.put(p12I, 2);
                assertThrows(IllegalArgumentException.class,
                                () -> new BitPruneMiningResult(tree, negativeValueMap, stats));
        }

        @Test
        @DisplayName("F1 count mismatch is rejected")
        void testF1CountMismatchRejected() {
                PrefixTree tree = createSampleTree(); // root children = 2 (p1, p2)
                Map<Pattern, Integer> supports = Map.of(p1, 2, p2, 3, p12I, 2);
                // f1Count = 1 instead of 2
                BitPruneStatistics statsMismatch = createSampleStatistics(3, 1, 2, 1, 1, 0, 0, 0, 2, 3, 3, 2);

                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                () -> new BitPruneMiningResult(tree, supports, statsMismatch));
                assertTrue(ex.getMessage().contains("f1Count"));
        }

        @Test
        @DisplayName("pattern count mismatch is rejected")
        void testPatternCountMismatchRejected() {
                PrefixTree tree = createSampleTree(); // 3 non-root patterns
                Map<Pattern, Integer> supports = Map.of(p1, 2, p2, 3, p12I, 2);

                // patternCount in statistics is 4 instead of 3
                BitPruneStatistics statsMismatch = createSampleStatistics(3, 2, 2, 2, 0, 0, 0, 0, 2, 3, 4, 2);

                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                () -> new BitPruneMiningResult(tree, supports, statsMismatch));
                assertTrue(ex.getMessage().contains("patternCount"));
        }

        @Test
        @DisplayName("root pattern does not appear in accepted patterns or supports")
        void testRootPatternNotAppearingInAcceptedPatternsOrSupports() {
                PrefixTree tree = createSampleTree();
                Map<Pattern, Integer> supports = Map.of(p1, 2, p2, 3, p12I, 2);
                BitPruneStatistics stats = createSampleStatistics(3, 2, 1, 1, 0, 0, 0, 0, 1, 3, 3, 1);

                BitPruneMiningResult result = new BitPruneMiningResult(tree, supports, stats);

                assertFalse(result.acceptedPatterns().contains(Pattern.empty()),
                                "acceptedPatterns must not contain root");
                assertFalse(result.supports().containsKey(Pattern.empty()),
                                "supports must not contain root");
                assertThrows(IllegalArgumentException.class,
                                () -> result.support(Pattern.empty()));
        }

        @Test
        @DisplayName("statistics snapshot is preserved as-is")
        void testStatisticsSnapshotPreserved() {
                PrefixTree tree = createSampleTree();
                Map<Pattern, Integer> supports = Map.of(p1, 2, p2, 3, p12I, 2);
                BitPruneStatistics stats = createSampleStatistics(3, 2, 1, 1, 0, 0, 0, 0, 1, 3, 3, 1);

                BitPruneMiningResult result = new BitPruneMiningResult(tree, supports, stats);

                assertSame(stats, result.statistics(), "statistics snapshot must be identical");
                assertSame(tree, result.prefixTree(), "prefixTree must be identical");
        }

        @Test
        @DisplayName("empty result on empty tree with 0 patterns is valid")
        void testEmptyResultIsValid() {
                PrefixTree tree = new PrefixTree();
                tree.freeze();

                Map<Pattern, Integer> supports = Map.of();
                BitPruneStatistics stats = createSampleStatistics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

                BitPruneMiningResult result = new BitPruneMiningResult(tree, supports, stats);

                assertTrue(result.acceptedPatterns().isEmpty());
                assertTrue(result.supports().isEmpty());
                assertEquals(0L, result.statistics().patternCount());
        }
}
