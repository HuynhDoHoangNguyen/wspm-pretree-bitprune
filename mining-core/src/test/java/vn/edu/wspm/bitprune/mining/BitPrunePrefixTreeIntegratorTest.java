package vn.edu.wspm.bitprune.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.mining.WeightedOnePattern;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.tree.ExtensionType;
import vn.edu.wspm.tree.PrefixTree;
import vn.edu.wspm.tree.PrefixTreeNode;

/**
 * Structural unit and integration tests for
 * {@link BitPrunePrefixTreeIntegrator}.
 */
class BitPrunePrefixTreeIntegratorTest {

        private BitPrunePrefixTreeIntegrator integrator;
        private BitPruneCandidateEngine engine;

        @BeforeEach
        void setUp() {
                integrator = new BitPrunePrefixTreeIntegrator();
                engine = new BitPruneCandidateEngine();
        }

        @Test
        void initializesSingletonF1RootsUnderEmptyRoot() {
                var tree = new PrefixTree();
                var f1 = List.of(
                                new WeightedOnePattern(1, 10),
                                new WeightedOnePattern(2, 20),
                                new WeightedOnePattern(3, 30));

                var roots = integrator.initializeF1Roots(tree, f1);

                assertEquals(3, roots.size());
                assertEquals(4, tree.nodeCount()); // root + 3 singletons

                for (int i = 0; i < roots.size(); i++) {
                        var node = roots.get(i);
                        int expectedItemId = i + 1;
                        var expectedPattern = Pattern.singleton(new Item(expectedItemId));

                        assertEquals(expectedPattern, node.pattern());
                        assertEquals(1, node.level());
                        assertEquals(ExtensionType.S_EXTENSION, node.extensionType().orElseThrow());
                        assertSame(tree.root(), node.parent().orElseThrow());
                        assertEquals(node, tree.find(expectedPattern).orElseThrow());
                }
        }

        @Test
        void initializesF1RootsFromUnsortedInputAndReturnsNumericOrder() {
                var tree = new PrefixTree();
                var f1 = List.of(
                                new WeightedOnePattern(3, 30),
                                new WeightedOnePattern(1, 10),
                                new WeightedOnePattern(2, 20));

                var roots = integrator.initializeF1Roots(tree, f1);

                assertEquals(3, roots.size());
                assertEquals("<(1)>", roots.get(0).pattern().serialize());
                assertEquals("<(2)>", roots.get(1).pattern().serialize());
                assertEquals("<(3)>", roots.get(2).pattern().serialize());
        }

        @Test
        void emptyF1LeavesTreeWithRootOnly() {
                var tree = new PrefixTree();
                var roots = integrator.initializeF1Roots(tree, List.of());

                assertTrue(roots.isEmpty());
                assertEquals(1, tree.nodeCount());
                assertTrue(tree.root().children().isEmpty());
        }

        @Test
        void initializeF1RootsDoesNotMutateInput() {
                var tree = new PrefixTree();
                var f1 = new ArrayList<WeightedOnePattern>();
                f1.add(new WeightedOnePattern(3, 30));
                f1.add(new WeightedOnePattern(1, 10));

                var f1Snapshot = List.copyOf(f1);
                integrator.initializeF1Roots(tree, f1);

                assertEquals(f1Snapshot, f1);
        }

        @Test
        void duplicateF1FailsBeforeAnyTreeMutation() {
                var tree = new PrefixTree();
                var f1WithDuplicates = List.of(
                                new WeightedOnePattern(1, 10),
                                new WeightedOnePattern(2, 20),
                                new WeightedOnePattern(1, 30));

                var ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> integrator.initializeF1Roots(tree, f1WithDuplicates));
                assertTrue(ex.getMessage().contains("duplicate item id"));
                // Assert zero nodes were inserted; root only remains
                assertEquals(1, tree.nodeCount());
                assertTrue(tree.root().children().isEmpty());
        }

        @Test
        void nullTreeF1AndElementsFailFast() {
                var tree = new PrefixTree();
                var validF1 = List.of(new WeightedOnePattern(1, 10));

                assertThrows(NullPointerException.class, () -> integrator.initializeF1Roots(null, validF1));
                assertThrows(NullPointerException.class, () -> integrator.initializeF1Roots(tree, null));

                var f1WithNull = new ArrayList<WeightedOnePattern>();
                f1WithNull.add(new WeightedOnePattern(1, 10));
                f1WithNull.add(null);
                assertThrows(NullPointerException.class, () -> integrator.initializeF1Roots(tree, f1WithNull));
                assertEquals(1, tree.nodeCount());
        }

        @Test
        void repeatedF1InitializationIsIdempotent() {
                var tree = new PrefixTree();
                var f1 = List.of(
                                new WeightedOnePattern(1, 10),
                                new WeightedOnePattern(2, 20));

                var firstRoots = integrator.initializeF1Roots(tree, f1);
                assertEquals(3, tree.nodeCount());

                var secondRoots = integrator.initializeF1Roots(tree, f1);
                assertEquals(3, tree.nodeCount());
                assertEquals(firstRoots.size(), secondRoots.size());
                for (int i = 0; i < firstRoots.size(); i++) {
                        assertSame(firstRoots.get(i), secondRoots.get(i));
                }
        }

        @Test
        void f1SupportMetadataDoesNotChangeStructuralRoots() {
                var tree1 = new PrefixTree();
                var tree2 = new PrefixTree();

                var f1HighSupport = List.of(new WeightedOnePattern(1, 500));
                var f1LowSupport = List.of(new WeightedOnePattern(1, 1));

                var roots1 = integrator.initializeF1Roots(tree1, f1HighSupport);
                var roots2 = integrator.initializeF1Roots(tree2, f1LowSupport);

                assertEquals(roots1.getFirst().pattern(), roots2.getFirst().pattern());
                assertEquals(tree1.nodeCount(), tree2.nodeCount());
        }

        @Test
        void multiDigitItemsPreserveReturnedNumericOrderButTreeSerializationOrder() {
                var tree = new PrefixTree();
                // Items 1, 2, 10 provided out of order
                var f1 = List.of(
                                new WeightedOnePattern(2, 20),
                                new WeightedOnePattern(10, 100),
                                new WeightedOnePattern(1, 10));

                var returnedRoots = integrator.initializeF1Roots(tree, f1);

                // 1. Returned roots must be in numeric ascending order: 1, 2, 10
                assertEquals(3, returnedRoots.size());
                assertEquals("<(1)>", returnedRoots.get(0).pattern().serialize());
                assertEquals("<(2)>", returnedRoots.get(1).pattern().serialize());
                assertEquals("<(10)>", returnedRoots.get(2).pattern().serialize());

                // 2. Tree internal child order is deterministic canonical string serialization
                // order:
                // "<(1)>" < "<(10)>" < "<(2)>" because '1' < '2' lexicographically
                var treeChildren = tree.root().children();
                assertEquals(3, treeChildren.size());
                assertEquals("<(1)>", treeChildren.get(0).pattern().serialize());
                assertEquals("<(10)>", treeChildren.get(1).pattern().serialize());
                assertEquals("<(2)>", treeChildren.get(2).pattern().serialize());

                // 3. Pre-order snapshot also reflects serialization order
                var preOrder = tree.preOrderPatterns();
                assertEquals(4, preOrder.size()); // root + 3 singletons
                assertEquals("<>", preOrder.get(0).serialize());
                assertEquals("<(1)>", preOrder.get(1).serialize());
                assertEquals("<(10)>", preOrder.get(2).serialize());
                assertEquals("<(2)>", preOrder.get(3).serialize());
        }

        @Test
        void insertsAcceptedIExtensionWithImmediateParentAndCorrectEdge() {
                var tree = new PrefixTree();
                var p1 = Pattern.singleton(new Item(1));
                integrator.initializeF1Roots(tree,
                                List.of(new WeightedOnePattern(1, 10), new WeightedOnePattern(2, 20)));

                var candidate = new BitPruneCandidateResult(
                                Pattern.of(Itemset.of(new Item(1), new Item(2))),
                                ExtensionType.I_EXTENSION,
                                new Item(2));

                var childNode = integrator.insertAcceptedChild(tree, p1, candidate);

                assertEquals(candidate.pattern(), childNode.pattern());
                assertEquals(ExtensionType.I_EXTENSION, childNode.extensionType().orElseThrow());
                assertEquals(2, childNode.level());
                assertSame(tree.find(p1).orElseThrow(), childNode.parent().orElseThrow());
                assertSame(childNode, tree.find(candidate.pattern()).orElseThrow());
                assertEquals(4, tree.nodeCount()); // root, (1), (2), (1 2)
        }

        @Test
        void insertsAcceptedSExtensionWithImmediateParentAndCorrectEdge() {
                var tree = new PrefixTree();
                var p1 = Pattern.singleton(new Item(1));
                integrator.initializeF1Roots(tree,
                                List.of(new WeightedOnePattern(1, 10), new WeightedOnePattern(2, 20)));

                var candidate = new BitPruneCandidateResult(
                                Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2))),
                                ExtensionType.S_EXTENSION,
                                new Item(2));

                var childNode = integrator.insertAcceptedChild(tree, p1, candidate);

                assertEquals(candidate.pattern(), childNode.pattern());
                assertEquals(ExtensionType.S_EXTENSION, childNode.extensionType().orElseThrow());
                assertEquals(2, childNode.level());
                assertSame(tree.find(p1).orElseThrow(), childNode.parent().orElseThrow());
                assertSame(childNode, tree.find(candidate.pattern()).orElseThrow());
        }

        @Test
        void insertsRepeatedItemSExtension() {
                var tree = new PrefixTree();
                var p1 = Pattern.singleton(new Item(1));
                integrator.initializeF1Roots(tree, List.of(new WeightedOnePattern(1, 10)));

                var candidate = new BitPruneCandidateResult(
                                Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(1))),
                                ExtensionType.S_EXTENSION,
                                new Item(1));

                var childNode = integrator.insertAcceptedChild(tree, p1, candidate);

                assertEquals("<(1)(1)>", childNode.pattern().serialize());
                assertEquals(ExtensionType.S_EXTENSION, childNode.extensionType().orElseThrow());
                assertSame(tree.find(p1).orElseThrow(), childNode.parent().orElseThrow());
        }

        @Test
        void itemsetAndSequenceExtensionRemainDistinctNodes() {
                var tree = new PrefixTree();
                var p1 = Pattern.singleton(new Item(1));
                integrator.initializeF1Roots(tree,
                                List.of(new WeightedOnePattern(1, 10), new WeightedOnePattern(2, 20)));

                var iCandidate = new BitPruneCandidateResult(
                                Pattern.of(Itemset.of(new Item(1), new Item(2))),
                                ExtensionType.I_EXTENSION,
                                new Item(2));
                var sCandidate = new BitPruneCandidateResult(
                                Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2))),
                                ExtensionType.S_EXTENSION,
                                new Item(2));

                var iNode = integrator.insertAcceptedChild(tree, p1, iCandidate);
                var sNode = integrator.insertAcceptedChild(tree, p1, sCandidate);

                assertFalse(iNode.pattern().equals(sNode.pattern()));
                assertEquals(5, tree.nodeCount()); // root, (1), (2), (1 2), (1)(2)

                var p1Node = tree.find(p1).orElseThrow();
                assertEquals(2, p1Node.children().size());
                assertTrue(p1Node.children().contains(iNode));
                assertTrue(p1Node.children().contains(sNode));
        }

        @Test
        void acceptedInsertionIsIdempotent() {
                var tree = new PrefixTree();
                var p1 = Pattern.singleton(new Item(1));
                integrator.initializeF1Roots(tree,
                                List.of(new WeightedOnePattern(1, 10), new WeightedOnePattern(2, 20)));

                var candidate = new BitPruneCandidateResult(
                                Pattern.of(Itemset.of(new Item(1), new Item(2))),
                                ExtensionType.I_EXTENSION,
                                new Item(2));

                var first = integrator.insertAcceptedChild(tree, p1, candidate);
                int nodeCountBefore = tree.nodeCount();

                var second = integrator.insertAcceptedChild(tree, p1, candidate);

                assertSame(first, second);
                assertEquals(nodeCountBefore, tree.nodeCount());
        }

        @Test
        void missingParentFailsWithoutMutation() {
                var tree = new PrefixTree();
                integrator.initializeF1Roots(tree, List.of(new WeightedOnePattern(1, 10)));
                int initialCount = tree.nodeCount();

                var missingParent = Pattern.singleton(new Item(99));
                var candidate = new BitPruneCandidateResult(
                                Pattern.of(Itemset.of(new Item(99), new Item(100))),
                                ExtensionType.I_EXTENSION,
                                new Item(100));

                var ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> integrator.insertAcceptedChild(tree, missingParent, candidate));
                assertTrue(ex.getMessage().contains("parent pattern does not exist in tree"));
                assertEquals(initialCount, tree.nodeCount());
        }

        @Test
        void mismatchedExtensionItemFailsWithoutMutation() {
                var tree = new PrefixTree();
                var p1 = Pattern.singleton(new Item(1));
                integrator.initializeF1Roots(tree,
                                List.of(new WeightedOnePattern(1, 10), new WeightedOnePattern(2, 20)));
                int initialCount = tree.nodeCount();

                // Pattern has item 2, but candidate claims extensionItem is 3
                var candidateWithWrongItem = new BitPruneCandidateResult(
                                Pattern.of(Itemset.of(new Item(1), new Item(2))),
                                ExtensionType.I_EXTENSION,
                                new Item(3));

                var ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> integrator.insertAcceptedChild(tree, p1, candidateWithWrongItem));
                assertTrue(ex.getMessage().contains("mismatched candidate pattern"));
                assertEquals(initialCount, tree.nodeCount());
        }

        @Test
        void mismatchedExtensionTypeFailsWithoutMutation() {
                var tree = new PrefixTree();
                var p1 = Pattern.singleton(new Item(1));
                integrator.initializeF1Roots(tree,
                                List.of(new WeightedOnePattern(1, 10), new WeightedOnePattern(2, 20)));
                int initialCount = tree.nodeCount();

                // Pattern is sequence <(1)(2)>, but candidate claims I_EXTENSION
                var candidateWithWrongType = new BitPruneCandidateResult(
                                Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2))),
                                ExtensionType.I_EXTENSION,
                                new Item(2));

                var ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> integrator.insertAcceptedChild(tree, p1, candidateWithWrongType));
                assertTrue(ex.getMessage().contains("mismatched candidate pattern"));
                assertEquals(initialCount, tree.nodeCount());
        }

        @Test
        void differentParentCandidateFailsIntegratorPrevalidationWithoutMutation() {
                var tree = new PrefixTree();
                var p1 = Pattern.singleton(new Item(1));
                var p2 = Pattern.singleton(new Item(2));
                integrator.initializeF1Roots(tree,
                                List.of(new WeightedOnePattern(1, 10), new WeightedOnePattern(2, 20)));

                // Insert <(1)(2)> under <(1)> with S_EXTENSION
                var sCandidate = new BitPruneCandidateResult(
                                Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2))),
                                ExtensionType.S_EXTENSION,
                                new Item(2));
                var existingNode = integrator.insertAcceptedChild(tree, p1, sCandidate);
                int countBefore = tree.nodeCount();

                // Candidate pattern <(1)(2)> presented under parent <(2)> fails integrator pre-validation
                var conflictingCandidate = new BitPruneCandidateResult(
                                Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2))),
                                ExtensionType.S_EXTENSION,
                                new Item(2));

                var ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> integrator.insertAcceptedChild(tree, p2, conflictingCandidate));
                assertTrue(ex.getMessage().contains("mismatched candidate pattern"));
                assertEquals(countBefore, tree.nodeCount());
                assertSame(existingNode, tree.find(conflictingCandidate.pattern()).orElseThrow());
                assertEquals(p1, existingNode.parent().orElseThrow().pattern());
                assertEquals(ExtensionType.S_EXTENSION, existingNode.extensionType().orElseThrow());
        }

        @Test
        void sharedPrefixTreeConflictingParentContractRemainsFailFast() {
                var tree = new PrefixTree();
                var p1 = Pattern.singleton(new Item(1));
                var p2 = Pattern.singleton(new Item(2));
                integrator.initializeF1Roots(tree,
                                List.of(new WeightedOnePattern(1, 10), new WeightedOnePattern(2, 20)));

                // Insert <(1)(2)> under <(1)> with S_EXTENSION
                var sCandidate = new BitPruneCandidateResult(
                                Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2))),
                                ExtensionType.S_EXTENSION,
                                new Item(2));
                var existingNode = integrator.insertAcceptedChild(tree, p1, sCandidate);
                int countBefore = tree.nodeCount();

                // Directly calling tree.insert with conflicting parent triggers shared PrefixTree conflict guard
                var childPattern = Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2)));
                var ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> tree.insert(p2, childPattern, ExtensionType.S_EXTENSION));
                assertTrue(ex.getMessage().contains("pattern already exists with a different parent or extension type"));
                assertEquals(countBefore, tree.nodeCount());
                assertSame(existingNode, tree.find(childPattern).orElseThrow());
                assertEquals(p1, existingNode.parent().orElseThrow().pattern());
                assertEquals(ExtensionType.S_EXTENSION, existingNode.extensionType().orElseThrow());
        }

        @Test
        void frozenTreeRejectsF1Initialization() {
                var tree = new PrefixTree();
                tree.freeze();

                var f1 = List.of(new WeightedOnePattern(1, 10));
                assertThrows(
                                IllegalStateException.class,
                                () -> integrator.initializeF1Roots(tree, f1));
                assertEquals(1, tree.nodeCount());
        }

        @Test
        void frozenTreeRejectsAcceptedChildInsertion() {
                var tree = new PrefixTree();
                var p1 = Pattern.singleton(new Item(1));
                integrator.initializeF1Roots(tree,
                                List.of(new WeightedOnePattern(1, 10), new WeightedOnePattern(2, 20)));
                tree.freeze();

                var candidate = new BitPruneCandidateResult(
                                Pattern.of(Itemset.of(new Item(1), new Item(2))),
                                ExtensionType.I_EXTENSION,
                                new Item(2));

                int nodeCountBefore = tree.nodeCount();
                assertThrows(
                                IllegalStateException.class,
                                () -> integrator.insertAcceptedChild(tree, p1, candidate));
                assertEquals(nodeCountBefore, tree.nodeCount());
        }

        @Test
        void generatedCandidateDoesNotEnterTreeUntilExplicitInsertion() {
                var tree = new PrefixTree();
                var p1 = Pattern.singleton(new Item(1));
                var f1 = List.of(
                                new WeightedOnePattern(1, 10),
                                new WeightedOnePattern(2, 20),
                                new WeightedOnePattern(3, 30));

                integrator.initializeF1Roots(tree, f1);

                // Generate candidates for <(1)>
                var candidates = engine.generateCandidates(p1, f1);
                assertEquals(5, candidates.size());

                // Assert NONE of the candidates are in the tree yet
                for (var cand : candidates) {
                        assertTrue(tree.find(cand.pattern()).isEmpty(),
                                        "Candidate must not exist in tree prior to insertion: "
                                                        + cand.pattern().serialize());
                }

                // Caller accepts ONLY candidate 0: <(1 2)>
                var acceptedCandidate = candidates.get(0);
                assertEquals("<(1 2)>", acceptedCandidate.pattern().serialize());
                integrator.insertAcceptedChild(tree, p1, acceptedCandidate);

                // Only candidate 0 is now present
                assertTrue(tree.find(acceptedCandidate.pattern()).isPresent());

                // The remaining 4 candidates are STILL not present
                for (int i = 1; i < candidates.size(); i++) {
                        assertTrue(tree.find(candidates.get(i).pattern()).isEmpty(),
                                        "Uninserted candidate must not exist in tree: "
                                                        + candidates.get(i).pattern().serialize());
                }
        }

        @Test
        void uninsertedRejectedCandidateNeverAppearsInTree() {
                var tree = new PrefixTree();
                var p1 = Pattern.singleton(new Item(1));
                var f1 = List.of(
                                new WeightedOnePattern(1, 10),
                                new WeightedOnePattern(3, 30));

                integrator.initializeF1Roots(tree, f1);

                // Candidate <(1 3)> is evaluated by caller and deemed "rejected"
                var rejectedCandidate = new BitPruneCandidateResult(
                                Pattern.of(Itemset.of(new Item(1), new Item(3))),
                                ExtensionType.I_EXTENSION,
                                new Item(3));

                // Caller never calls insertAcceptedChild
                assertTrue(tree.find(rejectedCandidate.pattern()).isEmpty());
                assertEquals(3, tree.nodeCount()); // root, (1), (3)
        }

        @Test
        void preOrderAndChildrenRemainDeterministic() {
                var tree = new PrefixTree();
                var f1 = List.of(
                                new WeightedOnePattern(1, 10),
                                new WeightedOnePattern(2, 20),
                                new WeightedOnePattern(3, 30));

                integrator.initializeF1Roots(tree, f1);

                var p1 = Pattern.singleton(new Item(1));
                integrator.insertAcceptedChild(
                                tree, p1,
                                new BitPruneCandidateResult(Pattern.of(Itemset.of(new Item(1), new Item(2))),
                                                ExtensionType.I_EXTENSION, new Item(2)));
                integrator.insertAcceptedChild(
                                tree, p1,
                                new BitPruneCandidateResult(
                                                Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2))),
                                                ExtensionType.S_EXTENSION, new Item(2)));

                var preOrder1 = tree.preOrderPatterns();
                var preOrder2 = tree.preOrderPatterns();
                assertEquals(preOrder1, preOrder2);

                var children1 = tree.find(p1).orElseThrow().children();
                var children2 = tree.find(p1).orElseThrow().children();
                assertEquals(children1.stream().map(PrefixTreeNode::pattern).toList(),
                                children2.stream().map(PrefixTreeNode::pattern).toList());
        }

        @Test
        void returnedNodeListsAreImmutable() {
                var tree = new PrefixTree();
                var f1 = List.of(new WeightedOnePattern(1, 10));

                var roots = integrator.initializeF1Roots(tree, f1);
                assertThrows(UnsupportedOperationException.class, () -> roots.add(roots.getFirst()));
        }

        @Test
        void controlledCatalogueStructuralIntegrationEvidence() {
                var universe = List.of(1, 2, 3, 4);
                var tree = new PrefixTree();

                var f1 = List.of(
                                new WeightedOnePattern(1, 10),
                                new WeightedOnePattern(2, 20),
                                new WeightedOnePattern(3, 30),
                                new WeightedOnePattern(4, 40));

                var roots = integrator.initializeF1Roots(tree, f1);
                assertEquals(4, roots.size());
                assertEquals(5, tree.nodeCount()); // root + 4 singletons

                // Bounded catalogue of structural candidates across pattern structural
                // varieties:
                record CandidateTestCase(Pattern parent, BitPruneCandidateResult candidate, int expectedLevel) {
                }

                var catalogue = List.of(
                                // 1. Singleton parent, I-extension
                                new CandidateTestCase(
                                                Pattern.of(Itemset.of(new Item(1))),
                                                new BitPruneCandidateResult(
                                                                Pattern.of(Itemset.of(new Item(1), new Item(2))),
                                                                ExtensionType.I_EXTENSION, new Item(2)),
                                                2),
                                // 2. Singleton parent, S-extension
                                new CandidateTestCase(
                                                Pattern.of(Itemset.of(new Item(1))),
                                                new BitPruneCandidateResult(
                                                                Pattern.of(Itemset.of(new Item(1)),
                                                                                Itemset.of(new Item(2))),
                                                                ExtensionType.S_EXTENSION, new Item(2)),
                                                2),
                                // 3. Singleton parent, repeated-item S-extension
                                new CandidateTestCase(
                                                Pattern.of(Itemset.of(new Item(1))),
                                                new BitPruneCandidateResult(
                                                                Pattern.of(Itemset.of(new Item(1)),
                                                                                Itemset.of(new Item(1))),
                                                                ExtensionType.S_EXTENSION, new Item(1)),
                                                2),
                                // 4. Parent with multi-item final itemset, I-extension: <(1 2)> -> <(1 2 3)>
                                new CandidateTestCase(
                                                Pattern.of(Itemset.of(new Item(1), new Item(2))),
                                                new BitPruneCandidateResult(
                                                                Pattern.of(Itemset.of(new Item(1), new Item(2),
                                                                                new Item(3))),
                                                                ExtensionType.I_EXTENSION, new Item(3)),
                                                3),
                                // 5. Parent with multi-item final itemset, S-extension: <(1 2)> -> <(1 2)(3)>
                                new CandidateTestCase(
                                                Pattern.of(Itemset.of(new Item(1), new Item(2))),
                                                new BitPruneCandidateResult(
                                                                Pattern.of(Itemset.of(new Item(1), new Item(2)),
                                                                                Itemset.of(new Item(3))),
                                                                ExtensionType.S_EXTENSION, new Item(3)),
                                                3),
                                // 6. Parent with multi-itemsets, I-extension: <(1)(2)> -> <(1)(2 3)>
                                new CandidateTestCase(
                                                Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2))),
                                                new BitPruneCandidateResult(
                                                                Pattern.of(Itemset.of(new Item(1)),
                                                                                Itemset.of(new Item(2), new Item(3))),
                                                                ExtensionType.I_EXTENSION, new Item(3)),
                                                3),
                                // 7. Parent with multi-itemsets, S-extension: <(1)(2)> -> <(1)(2)(4)>
                                new CandidateTestCase(
                                                Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2))),
                                                new BitPruneCandidateResult(Pattern.of(Itemset.of(new Item(1)),
                                                                Itemset.of(new Item(2)), Itemset.of(new Item(4))),
                                                                ExtensionType.S_EXTENSION, new Item(4)),
                                                3),
                                // 8. Parent with multi-itemsets, repeated S-extension: <(1)(2)> -> <(1)(2)(1)>
                                new CandidateTestCase(
                                                Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2))),
                                                new BitPruneCandidateResult(Pattern.of(Itemset.of(new Item(1)),
                                                                Itemset.of(new Item(2)), Itemset.of(new Item(1))),
                                                                ExtensionType.S_EXTENSION, new Item(1)),
                                                3),
                                // 9. Size-3 parent, I-extension: <(1 2 3)> -> <(1 2 3 4)>
                                new CandidateTestCase(
                                                Pattern.of(Itemset.of(new Item(1), new Item(2), new Item(3))),
                                                new BitPruneCandidateResult(
                                                                Pattern.of(Itemset.of(new Item(1), new Item(2),
                                                                                new Item(3), new Item(4))),
                                                                ExtensionType.I_EXTENSION, new Item(4)),
                                                4));

                int currentCount = tree.nodeCount();
                for (var testCase : catalogue) {
                        var parentNode = tree.find(testCase.parent()).orElseThrow(
                                        () -> new AssertionError(
                                                        "Parent must exist in tree: " + testCase.parent().serialize()));

                        var childNode = integrator.insertAcceptedChild(tree, testCase.parent(), testCase.candidate());
                        currentCount++;

                        // Exact pattern identity
                        assertEquals(testCase.candidate().pattern(), childNode.pattern());
                        // Parent node identity
                        assertSame(parentNode, childNode.parent().orElseThrow());
                        // ExtensionType
                        assertEquals(testCase.candidate().extensionType(), childNode.extensionType().orElseThrow());
                        // Node level
                        assertEquals(testCase.expectedLevel(), childNode.level());
                        // NodeCount delta
                        assertEquals(currentCount, tree.nodeCount());
                        // find(pattern)
                        assertSame(childNode, tree.find(testCase.candidate().pattern()).orElseThrow());

                        // Idempotent reinsertion: returns same node, nodeCount delta is 0
                        var reinserted = integrator.insertAcceptedChild(tree, testCase.parent(), testCase.candidate());
                        assertSame(childNode, reinserted);
                        assertEquals(currentCount, tree.nodeCount());

                        // Deterministic pre-order snapshot
                        var preOrder = tree.preOrderPatterns();
                        assertNotNull(preOrder);
                        assertEquals(tree.nodeCount(), preOrder.size());
                }

                System.out.printf("C09.2 Controlled catalogue: %d structural candidates successfully verified%n",
                                catalogue.size());
        }
}
