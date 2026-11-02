package vn.edu.wspm.bitprune.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
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
 * Integrated structural differential evidence for the complete C09 candidate generation
 * and accepted-child Prefix Tree integration pipeline against an independent direct oracle.
 *
 * <p>Pipeline under evidence:
 * F1 root initialization
 * &rarr; pure structural candidate generation
 * &rarr; caller-controlled candidate selection
 * &rarr; accepted-child PrefixTree insertion
 * &rarr; immediate parent/edge verification
 * &rarr; deterministic tree structure.</p>
 *
 * <p>The direct oracle constructs expected patterns directly from raw immutable lists
 * without invoking {@link BitPruneCandidateEngine}, {@code PatternExtensions}, baseline
 * {@code CandidateEngine}, or PRISM joins.</p>
 */
class BitPruneCandidatePrefixTreeDifferentialTest {

    private static final long FIXED_SEED = 20261102L;
    private static final int RANDOM_TRIALS = 1500;

    private BitPruneCandidateEngine engine;
    private BitPrunePrefixTreeIntegrator integrator;

    @BeforeEach
    void setUp() {
        engine = new BitPruneCandidateEngine();
        integrator = new BitPrunePrefixTreeIntegrator();
    }

    // =========================================================================
    // Independent Direct Structural Oracle & Path Builder
    // =========================================================================

    /**
     * Independent direct structural oracle built strictly from raw immutable lists without calling
     * {@link BitPruneCandidateEngine}, {@link BitPrunePrefixTreeIntegrator}, {@code PatternExtensions},
     * baseline {@code CandidateEngine}, or PRISM join operations.
     */
    private static List<BitPruneCandidateResult> directStructuralOracle(
            Pattern parent,
            List<WeightedOnePattern> f1) {
        Objects.requireNonNull(parent, "parent must not be null");
        if (parent.isEmpty()) {
            throw new IllegalArgumentException("parent must not be empty");
        }
        Objects.requireNonNull(f1, "f1 must not be null");
        if (f1.isEmpty()) {
            return List.of();
        }

        var seenIds = new HashSet<Integer>(f1.size());
        for (var p : f1) {
            Objects.requireNonNull(p, "f1 element must not be null");
            if (!seenIds.add(p.itemId())) {
                throw new IllegalArgumentException("duplicate item id in F1: " + p.itemId());
            }
        }

        var sortedF1 = new ArrayList<>(f1);
        sortedF1.sort(Comparator.comparingInt(WeightedOnePattern::itemId));

        var expected = new ArrayList<BitPruneCandidateResult>();
        var parentItemsets = parent.itemsets();
        int parentItemsetCount = parentItemsets.size();
        var lastItemset = parentItemsets.getLast();
        int lastItemId = lastItemset.items().getLast().id();

        // 1. Raw rebuild of I-Extensions (items strictly greater than lastItemId)
        for (var p : sortedF1) {
            int x = p.itemId();
            if (x > lastItemId) {
                var extItem = new Item(x);
                var newItems = new ArrayList<>(lastItemset.items());
                newItems.add(extItem);
                var newLastItemset = new Itemset(newItems);

                var newItemsets = new ArrayList<Itemset>(parentItemsetCount);
                for (int i = 0; i < parentItemsetCount - 1; i++) {
                    newItemsets.add(parentItemsets.get(i));
                }
                newItemsets.add(newLastItemset);
                Pattern child = new Pattern(newItemsets);
                expected.add(new BitPruneCandidateResult(child, ExtensionType.I_EXTENSION, extItem));
            }
        }

        // 2. Raw rebuild of S-Extensions (append new singleton itemset (x))
        for (var p : sortedF1) {
            int x = p.itemId();
            var extItem = new Item(x);
            var newItemsets = new ArrayList<Itemset>(parentItemsetCount + 1);
            newItemsets.addAll(parentItemsets);
            newItemsets.add(new Itemset(List.of(extItem)));
            Pattern child = new Pattern(newItemsets);
            expected.add(new BitPruneCandidateResult(child, ExtensionType.S_EXTENSION, extItem));
        }

        return List.copyOf(expected);
    }

    private static Pattern rawExtendI(Pattern parent, Item item) {
        var parentItemsets = parent.itemsets();
        int count = parentItemsets.size();
        var lastItemset = parentItemsets.getLast();
        var newItems = new ArrayList<>(lastItemset.items());
        newItems.add(item);
        var newLastItemset = new Itemset(newItems);

        var newItemsets = new ArrayList<Itemset>(count);
        for (int i = 0; i < count - 1; i++) {
            newItemsets.add(parentItemsets.get(i));
        }
        newItemsets.add(newLastItemset);
        return new Pattern(newItemsets);
    }

    private static Pattern rawExtendS(Pattern parent, Item item) {
        var newItemsets = new ArrayList<Itemset>(parent.itemsets().size() + 1);
        newItemsets.addAll(parent.itemsets());
        newItemsets.add(new Itemset(List.of(item)));
        return new Pattern(newItemsets);
    }

    /**
     * Constructs canonical ancestor path from root to targetParent using direct raw extension
     * and explicit accepted-child insertion.
     */
    private void constructAncestorPath(
            PrefixTree tree,
            Pattern targetParent) {
        if (targetParent.isEmpty()) {
            return;
        }

        var itemsets = targetParent.itemsets();
        var firstItemset = itemsets.getFirst();
        var firstItems = firstItemset.items();

        // 1. Root singleton: must already exist in tree from initializeF1Roots
        Pattern current = Pattern.singleton(firstItems.getFirst());
        if (tree.find(current).isEmpty()) {
            fail("Singleton root must already exist in PrefixTree: " + current.serialize());
        }

        // 2. Remaining items in first itemset via I-Extension
        for (int i = 1; i < firstItems.size(); i++) {
            Item item = firstItems.get(i);
            Pattern nextI = rawExtendI(current, item);
            if (tree.find(nextI).isEmpty()) {
                integrator.insertAcceptedChild(
                        tree, current,
                        new BitPruneCandidateResult(nextI, ExtensionType.I_EXTENSION, item));
            }
            current = nextI;
        }

        // 3. Subsequent itemsets: first item via S-Extension, remaining via I-Extension
        for (int s = 1; s < itemsets.size(); s++) {
            var set = itemsets.get(s);
            var items = set.items();
            Item firstItem = items.getFirst();
            Pattern nextS = rawExtendS(current, firstItem);
            if (tree.find(nextS).isEmpty()) {
                integrator.insertAcceptedChild(
                        tree, current,
                        new BitPruneCandidateResult(nextS, ExtensionType.S_EXTENSION, firstItem));
            }
            current = nextS;

            for (int i = 1; i < items.size(); i++) {
                Item item = items.get(i);
                Pattern nextI = rawExtendI(current, item);
                if (tree.find(nextI).isEmpty()) {
                    integrator.insertAcceptedChild(
                            tree, current,
                            new BitPruneCandidateResult(nextI, ExtensionType.I_EXTENSION, item));
                }
                current = nextI;
            }
        }

        assertEquals(targetParent, current);
        assertTrue(tree.find(targetParent).isPresent());
    }

    /**
     * Complete test-only diagnostic formatter for candidate generation and tree integration mismatches.
     */
    private static String formatMismatchDiagnostic(
            String mode,
            Long seed,
            Integer trial,
            Integer presentationIndex,
            Integer comparisonIndex,
            List<WeightedOnePattern> f1,
            Pattern parent,
            List<BitPruneCandidateResult> expected,
            List<BitPruneCandidateResult> actual,
            List<Boolean> selectionMask,
            int nodeCountBefore,
            int nodeCountAfter,
            List<Pattern> preOrderBefore,
            List<Pattern> preOrderAfter) {
        var sb = new StringBuilder();
        sb.append("\n================ C09.3 PIPELINE MISMATCH ================\n");
        sb.append("Evidence Mode: ").append(mode).append("\n");
        if (seed != null) {
            sb.append("Fixed Seed: ").append(seed).append(", Trial: ").append(trial).append("\n");
        }
        if (presentationIndex != null) {
            sb.append("Presentation Index: ").append(presentationIndex)
              .append(", Comparison Index: ").append(comparisonIndex).append("\n");
        }
        sb.append("F1 Presentation: ").append(f1).append("\n");
        sb.append("Parent Pattern: ").append(parent.serialize()).append("\n");
        if (selectionMask != null) {
            sb.append("Selection Retention Mask: ").append(selectionMask).append("\n");
        }
        sb.append("Tree Node Count (before -> after): ").append(nodeCountBefore).append(" -> ").append(nodeCountAfter).append("\n");
        sb.append("Tree Pre-order (before): ").append(preOrderBefore.stream().map(Pattern::serialize).toList()).append("\n");
        if (preOrderAfter != null) {
            sb.append("Tree Pre-order (after):  ").append(preOrderAfter.stream().map(Pattern::serialize).toList()).append("\n");
        }

        sb.append("Expected Candidates Count: ").append(expected.size()).append("\n");
        sb.append("Actual Candidates Count:   ").append(actual.size()).append("\n");

        int firstDiffIndex = -1;
        int minLen = Math.min(expected.size(), actual.size());
        for (int i = 0; i < minLen; i++) {
            if (!expected.get(i).equals(actual.get(i))) {
                firstDiffIndex = i;
                break;
            }
        }
        if (firstDiffIndex == -1 && expected.size() != actual.size()) {
            firstDiffIndex = minLen;
        }

        sb.append("First Differing Index: ").append(firstDiffIndex).append("\n");
        if (firstDiffIndex >= 0) {
            if (firstDiffIndex < expected.size()) {
                var expCand = expected.get(firstDiffIndex);
                sb.append("  Expected at [").append(firstDiffIndex).append("]: ")
                  .append(expCand.pattern().serialize())
                  .append(" | Type: ").append(expCand.extensionType())
                  .append(" | Item: ").append(expCand.extensionItem().id()).append("\n");
            } else {
                sb.append("  Expected at [").append(firstDiffIndex).append("]: <OUT OF BOUNDS>\n");
            }
            if (firstDiffIndex < actual.size()) {
                var actCand = actual.get(firstDiffIndex);
                sb.append("  Actual at [").append(firstDiffIndex).append("]:   ")
                  .append(actCand.pattern().serialize())
                  .append(" | Type: ").append(actCand.extensionType())
                  .append(" | Item: ").append(actCand.extensionItem().id()).append("\n");
            } else {
                sb.append("  Actual at [").append(firstDiffIndex).append("]:   <OUT OF BOUNDS>\n");
            }
        }
        sb.append("Expected Candidate List: ").append(expected.stream().map(c -> c.pattern().serialize() + "[" + c.extensionType() + ":" + c.extensionItem().id() + "]").toList()).append("\n");
        sb.append("Actual Candidate List:   ").append(actual.stream().map(c -> c.pattern().serialize() + "[" + c.extensionType() + ":" + c.extensionItem().id() + "]").toList()).append("\n");
        sb.append("=========================================================\n");
        return sb.toString();
    }

    // =========================================================================
    // 1. Exhaustive Small-Universe Catalogue (9,540 Comparisons)
    // =========================================================================

    @Test
    void exhaustiveSmallUniverseCatalogueEvidence() {
        var universe = List.of(1, 2, 3, 4);

        // 1. Generate all 64 ordered F1 presentations
        var presentations = new ArrayList<List<WeightedOnePattern>>();
        for (int k = 1; k <= 4; k++) {
            var combinations = generateCombinations(universe, k);
            for (var combo : combinations) {
                var perms = generatePermutations(combo);
                for (var perm : perms) {
                    var f1 = perm.stream()
                            .map(id -> new WeightedOnePattern(id, id * 10))
                            .toList();
                    presentations.add(f1);
                }
            }
        }

        assertEquals(64, presentations.size());
        assertEquals(4, presentations.stream().filter(p -> p.size() == 1).count());
        assertEquals(12, presentations.stream().filter(p -> p.size() == 2).count());
        assertEquals(24, presentations.stream().filter(p -> p.size() == 3).count());
        assertEquals(24, presentations.stream().filter(p -> p.size() == 4).count());

        // 2. Generate 304 bounded parent catalogue
        var itemsetSubsets = new ArrayList<Itemset>();
        for (int k = 1; k <= 4; k++) {
            for (var combo : generateCombinations(universe, k)) {
                var items = combo.stream().sorted().map(Item::new).toList();
                itemsetSubsets.add(new Itemset(items));
            }
        }
        assertEquals(15, itemsetSubsets.size());

        var parentCatalogue = new ArrayList<Pattern>();
        // Category 1: 1-itemset patterns (15)
        for (var s : itemsetSubsets) {
            parentCatalogue.add(Pattern.of(s));
        }
        // Category 2: 2-itemsets patterns (15 x 15 = 225)
        for (var s1 : itemsetSubsets) {
            for (var s2 : itemsetSubsets) {
                parentCatalogue.add(Pattern.of(s1, s2));
            }
        }
        // Category 3: 3-singleton patterns (4^3 = 64)
        var singletons = universe.stream().map(id -> Itemset.of(new Item(id))).toList();
        for (var s1 : singletons) {
            for (var s2 : singletons) {
                for (var s3 : singletons) {
                    parentCatalogue.add(Pattern.of(s1, s2, s3));
                }
            }
        }
        assertEquals(304, parentCatalogue.size());

        int presentationIndex = 0;
        int compatibleComparisons = 0;

        for (var f1 : presentations) {
            presentationIndex++;
            var f1ItemIds = f1.stream().map(WeightedOnePattern::itemId).collect(HashSet::new, Set::add, Set::addAll);

            for (var parent : parentCatalogue) {
                // Check compatibility: all parent items must belong to F1
                boolean compatible = parent.itemsets().stream()
                        .flatMap(s -> s.items().stream())
                        .allMatch(item -> f1ItemIds.contains(item.id()));

                if (!compatible) {
                    continue;
                }
                compatibleComparisons++;
                var f1Snapshot = List.copyOf(f1);

                // Fresh tree setup
                var tree = new PrefixTree();
                var rootNodes = integrator.initializeF1Roots(tree, f1);
                assertEquals(f1.size(), rootNodes.size());
                assertEquals(f1.size() + 1, tree.nodeCount());

                // Build accepted ancestor path
                constructAncestorPath(tree, parent);
                var parentNode = tree.find(parent).orElseThrow();
                int countBefore = tree.nodeCount();
                var preOrderBefore = tree.preOrderPatterns();

                // Candidate generation
                var actualCandidates = engine.generateCandidates(parent, f1);

                // Tree must not mutate during generation
                assertEquals(countBefore, tree.nodeCount(), "generator must not mutate node count");
                assertEquals(preOrderBefore, tree.preOrderPatterns(), "generator must not mutate pre-order snapshot");

                // Oracle comparison with complete fail-fast diagnostics
                var expectedCandidates = directStructuralOracle(parent, f1);
                if (!expectedCandidates.equals(actualCandidates)) {
                    fail(formatMismatchDiagnostic(
                            "exhaustive", null, null, presentationIndex, compatibleComparisons,
                            f1, parent, expectedCandidates, actualCandidates, null,
                            countBefore, tree.nodeCount(), preOrderBefore, tree.preOrderPatterns()));
                }

                // Insert all generated candidates
                var insertedNodes = new ArrayList<PrefixTreeNode>();
                for (var cand : actualCandidates) {
                    var childNode = integrator.insertAcceptedChild(tree, parent, cand);
                    insertedNodes.add(childNode);

                    // Assertions per candidate node
                    assertSame(childNode, tree.find(cand.pattern()).orElseThrow());
                    assertSame(parentNode, childNode.parent().orElseThrow());
                    assertEquals(cand.extensionType(), childNode.extensionType().orElseThrow());
                    assertEquals(parentNode.level() + 1, childNode.level());

                    // Assert extension item matches structural delta
                    Item deltaItem = cand.pattern().lastItem().orElseThrow();
                    assertEquals(cand.extensionItem(), deltaItem);

                    // Length and itemset boundary checks
                    assertEquals(parent.length() + 1, cand.pattern().length());
                    if (cand.extensionType() == ExtensionType.I_EXTENSION) {
                        assertEquals(parent.size(), cand.pattern().size());
                    } else {
                        assertEquals(parent.size() + 1, cand.pattern().size());
                    }
                }

                // Node count delta equals candidate count
                assertEquals(countBefore + actualCandidates.size(), tree.nodeCount());

                // Idempotent reinsertion
                for (var cand : actualCandidates) {
                    var reinserted = integrator.insertAcceptedChild(tree, parent, cand);
                    assertSame(tree.find(cand.pattern()).orElseThrow(), reinserted);
                }
                assertEquals(countBefore + actualCandidates.size(), tree.nodeCount());

                // Independent child ordering check: direct oracle candidates sorted by shared PrefixTree serialization contract
                var expectedChildPatterns = expectedCandidates.stream()
                        .map(BitPruneCandidateResult::pattern)
                        .sorted(Comparator.comparing(Pattern::serialize))
                        .toList();

                var actualChildPatterns = parentNode.children().stream()
                        .map(PrefixTreeNode::pattern)
                        .toList();

                assertEquals(expectedChildPatterns, actualChildPatterns,
                        "parentNode.children() must match independent serialization order of expected candidates");

                // Deterministic snapshot check across repeated calls
                var preOrder1 = tree.preOrderPatterns();
                var preOrder2 = tree.preOrderPatterns();
                assertEquals(preOrder1, preOrder2, "tree pre-order snapshots must be stable across repeated calls");

                // F1 immutability assertion
                assertEquals(f1Snapshot, f1, "F1 presentation must not be mutated across exhaustive pipeline");
            }
        }

        assertEquals(9540, compatibleComparisons, "compatible comparisons must equal 9540");

        System.out.printf("C09.3 Exhaustive small-universe: 304 parents x 64 F1 presentations = %d compatible comparisons, 0 mismatches%n",
                compatibleComparisons);
    }

    // =========================================================================
    // 2. Fixed-Seed Randomized Integrated Evidence (1,500 Trials)
    // =========================================================================

    @Test
    void fixedSeedRandomizedIntegratedEvidence() {
        var random = new Random(FIXED_SEED);

        int completedTrials = 0;
        int zeroCandidateCases = 0;
        int positiveCandidateCases = 0;
        int noneSelectedCases = 0;
        int allSelectedCases = 0;
        int partiallySelectedCases = 0;
        int iExtensionInsertions = 0;
        int sExtensionInsertions = 0;
        int repeatedItemSExtensionInsertions = 0;
        int multiItemsetParentCases = 0;
        int multiItemFinalItemsetCases = 0;
        int multiDigitItemCases = 0;

        for (int trial = 0; trial < RANDOM_TRIALS; trial++) {
            completedTrials++;

            // Handle trial 0 as explicit boundary with empty F1 to verify zeroCandidateCases
            List<WeightedOnePattern> f1;
            Pattern parent;

            if (trial == 0) {
                f1 = List.of();
                parent = Pattern.singleton(new Item(1));
            } else {
                // Select k items between 1 and 15
                int k = 1 + random.nextInt(5); // 1..5 items
                var pool = new ArrayList<Integer>();
                for (int i = 1; i <= 15; i++) {
                    pool.add(i);
                }
                Collections.shuffle(pool, random);
                var selectedItemIds = new ArrayList<>(pool.subList(0, k));

                boolean hasMultiDigit = selectedItemIds.stream().anyMatch(id -> id >= 10);
                if (hasMultiDigit) {
                    multiDigitItemCases++;
                }

                // Random presentation shuffle
                Collections.shuffle(selectedItemIds, random);
                f1 = selectedItemIds.stream()
                        .map(id -> new WeightedOnePattern(id, id * 10))
                        .toList();

                // Select canonical parent using only items from F1
                int itemsetCount = 1 + random.nextInt(3); // 1..3 itemsets
                if (itemsetCount > 1) {
                    multiItemsetParentCases++;
                }

                var parentSets = new ArrayList<Itemset>(itemsetCount);
                for (int s = 0; s < itemsetCount; s++) {
                    int inSetCount = 1 + random.nextInt(Math.min(3, selectedItemIds.size()));
                    var setPool = new ArrayList<>(selectedItemIds);
                    Collections.shuffle(setPool, random);
                    var setItems = setPool.subList(0, inSetCount).stream()
                            .sorted()
                            .map(Item::new)
                            .toList();
                    parentSets.add(new Itemset(setItems));
                }
                if (parentSets.getLast().size() > 1) {
                    multiItemFinalItemsetCases++;
                }
                parent = new Pattern(parentSets);
            }

            var f1Snapshot = List.copyOf(f1);

            var tree = new PrefixTree();
            integrator.initializeF1Roots(tree, f1);

            int countBefore;
            if (trial == 0) {
                countBefore = tree.nodeCount(); // root only (1)
            } else {
                constructAncestorPath(tree, parent);
                countBefore = tree.nodeCount();
            }
            var preOrderBefore = tree.preOrderPatterns();

            var actual = engine.generateCandidates(parent, f1);
            var expected = directStructuralOracle(parent, f1);

            // Caller-controlled selection retention mask generated before mismatch evaluation
            List<Boolean> mask;
            if (expected.isEmpty()) {
                mask = List.of();
            } else {
                mask = new ArrayList<>(expected.size());
                if (trial % 8 == 0) {
                    // Select none
                    for (int i = 0; i < expected.size(); i++) mask.add(false);
                } else if (trial % 8 == 1) {
                    // Select all
                    for (int i = 0; i < expected.size(); i++) mask.add(true);
                } else {
                    // Random coin flip
                    for (int i = 0; i < expected.size(); i++) {
                        mask.add(random.nextBoolean());
                    }
                }
            }

            if (!expected.equals(actual)) {
                fail(formatMismatchDiagnostic(
                        "randomized", FIXED_SEED, trial, null, null,
                        f1, parent, expected, actual, mask,
                        countBefore, tree.nodeCount(), preOrderBefore, tree.preOrderPatterns()));
            }

            assertEquals(countBefore, tree.nodeCount(), "generator must not mutate tree");
            assertEquals(preOrderBefore, tree.preOrderPatterns(), "generator must not mutate pre-order snapshot");

            if (actual.isEmpty()) {
                zeroCandidateCases++;
                noneSelectedCases++;
                assertEquals(f1Snapshot, f1, "F1 presentation must not be mutated across randomized trial");
                continue;
            } else {
                positiveCandidateCases++;
            }

            boolean hasTrue = mask.contains(true);
            boolean hasFalse = mask.contains(false);
            if (hasTrue && hasFalse) {
                partiallySelectedCases++;
            } else if (hasTrue) {
                allSelectedCases++;
            } else {
                noneSelectedCases++;
            }

            // Derive expected selected candidates strictly from direct oracle
            var expectedSelected = new ArrayList<BitPruneCandidateResult>();
            for (int i = 0; i < expected.size(); i++) {
                if (mask.get(i)) {
                    expectedSelected.add(expected.get(i));
                }
            }

            // Partition actual candidates into selected and unselected
            var selected = new ArrayList<BitPruneCandidateResult>();
            var unselected = new ArrayList<BitPruneCandidateResult>();
            for (int i = 0; i < actual.size(); i++) {
                if (mask.get(i)) {
                    selected.add(actual.get(i));
                } else {
                    unselected.add(actual.get(i));
                }
            }

            var parentItems = parent.itemsets().stream()
                    .flatMap(s -> s.items().stream())
                    .map(Item::id)
                    .collect(HashSet::new, Set::add, Set::addAll);

            // Insert selected candidates only
            for (var cand : selected) {
                var childNode = integrator.insertAcceptedChild(tree, parent, cand);
                assertSame(tree.find(parent).orElseThrow(), childNode.parent().orElseThrow());
                assertEquals(cand.extensionType(), childNode.extensionType().orElseThrow());

                if (cand.extensionType() == ExtensionType.I_EXTENSION) {
                    iExtensionInsertions++;
                } else {
                    sExtensionInsertions++;
                    if (parentItems.contains(cand.extensionItem().id())) {
                        repeatedItemSExtensionInsertions++;
                    }
                }
            }

            // Verification: selected candidates present, unselected absent
            for (var cand : selected) {
                assertTrue(tree.find(cand.pattern()).isPresent(),
                        "selected candidate must be present in PrefixTree: " + cand.pattern().serialize());
            }
            for (var cand : unselected) {
                assertTrue(tree.find(cand.pattern()).isEmpty(),
                        "unselected candidate must NOT be present in PrefixTree: " + cand.pattern().serialize());
            }

            // Node count delta
            assertEquals(countBefore + selected.size(), tree.nodeCount());

            // Idempotent reinsertion
            for (var cand : selected) {
                var reinserted = integrator.insertAcceptedChild(tree, parent, cand);
                assertSame(tree.find(cand.pattern()).orElseThrow(), reinserted);
            }
            assertEquals(countBefore + selected.size(), tree.nodeCount());

            // Independent child ordering check: direct-oracle selected candidates sorted by shared PrefixTree serialization contract
            var parentNode = tree.find(parent).orElseThrow();
            var expectedSelectedChildPatterns = expectedSelected.stream()
                    .map(BitPruneCandidateResult::pattern)
                    .sorted(Comparator.comparing(Pattern::serialize))
                    .toList();

            var actualChildPatterns = parentNode.children().stream()
                    .map(PrefixTreeNode::pattern)
                    .toList();

            assertEquals(expectedSelectedChildPatterns, actualChildPatterns,
                    "parentNode.children() must match independent serialization order of direct-oracle selected candidates");

            // F1 immutability assertion
            assertEquals(f1Snapshot, f1, "F1 presentation must not be mutated across randomized trial");
        }

        // Coverage assertions
        assertEquals(RANDOM_TRIALS, completedTrials);
        assertTrue(zeroCandidateCases >= 1, "zero candidate cases must be >= 1");
        assertTrue(positiveCandidateCases >= 1, "positive candidate cases must be >= 1");
        assertTrue(noneSelectedCases >= 1, "none selected cases must be >= 1");
        assertTrue(allSelectedCases >= 1, "all selected cases must be >= 1");
        assertTrue(partiallySelectedCases >= 1, "partially selected cases must be >= 1");
        assertTrue(iExtensionInsertions >= 1, "I-extension insertions must be >= 1");
        assertTrue(sExtensionInsertions >= 1, "S-extension insertions must be >= 1");
        assertTrue(repeatedItemSExtensionInsertions >= 1, "repeated-item S-extension insertions must be >= 1");
        assertTrue(multiItemsetParentCases >= 1, "multi-itemset parent cases must be >= 1");
        assertTrue(multiItemFinalItemsetCases >= 1, "multi-item final itemset cases must be >= 1");
        assertTrue(multiDigitItemCases >= 1, "multi-digit item cases must be >= 1");

        System.out.printf("C09.3 Fixed-seed randomized: %d trials (seed=%dL), 0 mismatches%n", completedTrials, FIXED_SEED);
    }

    // =========================================================================
    // 3. Deterministic Boundary Tests
    // =========================================================================

    @Test
    void emptyF1LeavesTreeWithRootOnlyAndZeroRoots() {
        var tree = new PrefixTree();
        var f1 = List.<WeightedOnePattern>of();

        var roots = integrator.initializeF1Roots(tree, f1);
        assertTrue(roots.isEmpty());
        assertEquals(1, tree.nodeCount());
        assertTrue(tree.root().children().isEmpty());
    }

    @Test
    void singletonF1AndSingletonParentProducesOnlyRepeatedSExtension() {
        var tree = new PrefixTree();
        var f1 = List.of(new WeightedOnePattern(2, 20));
        integrator.initializeF1Roots(tree, f1);

        var parent = Pattern.singleton(new Item(2));
        var candidates = engine.generateCandidates(parent, f1);

        // No item in F1 > 2, so I-extension is impossible. Only S-extension <(2)(2)>
        assertEquals(1, candidates.size());
        var onlyCand = candidates.getFirst();
        assertEquals(ExtensionType.S_EXTENSION, onlyCand.extensionType());
        assertEquals(new Item(2), onlyCand.extensionItem());
        assertEquals(Pattern.of(Itemset.of(new Item(2)), Itemset.of(new Item(2))), onlyCand.pattern());

        var childNode = integrator.insertAcceptedChild(tree, parent, onlyCand);
        assertEquals(3, tree.nodeCount()); // root, (2), (2)(2)
        assertSame(tree.find(parent).orElseThrow(), childNode.parent().orElseThrow());
    }

    @Test
    void parentWithMultiItemFinalItemsetGeneratesCorrectCandidatesAndIntegrates() {
        var tree = new PrefixTree();
        var f1 = List.of(
                new WeightedOnePattern(1, 10),
                new WeightedOnePattern(2, 20),
                new WeightedOnePattern(3, 30));
        integrator.initializeF1Roots(tree, f1);

        var parent = Pattern.of(Itemset.of(new Item(1), new Item(2)));
        constructAncestorPath(tree, parent);

        var candidates = engine.generateCandidates(parent, f1);
        // I-extension: x > 2 in F1 -> item 3: <(1 2 3)>
        // S-extensions: items 1, 2, 3: <(1 2)(1)>, <(1 2)(2)>, <(1 2)(3)>
        assertEquals(4, candidates.size());
        assertEquals(ExtensionType.I_EXTENSION, candidates.get(0).extensionType());
        assertEquals(new Item(3), candidates.get(0).extensionItem());
        assertEquals(ExtensionType.S_EXTENSION, candidates.get(1).extensionType());
        assertEquals(new Item(1), candidates.get(1).extensionItem());
        assertEquals(ExtensionType.S_EXTENSION, candidates.get(2).extensionType());
        assertEquals(new Item(2), candidates.get(2).extensionItem());
        assertEquals(ExtensionType.S_EXTENSION, candidates.get(3).extensionType());
        assertEquals(new Item(3), candidates.get(3).extensionItem());

        for (var c : candidates) {
            var node = integrator.insertAcceptedChild(tree, parent, c);
            assertSame(tree.find(parent).orElseThrow(), node.parent().orElseThrow());
        }
    }

    @Test
    void parentWithMultipleItemsetsGeneratesCorrectCandidatesAndIntegrates() {
        var tree = new PrefixTree();
        var f1 = List.of(
                new WeightedOnePattern(1, 10),
                new WeightedOnePattern(2, 20),
                new WeightedOnePattern(3, 30));
        integrator.initializeF1Roots(tree, f1);

        var parent = Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2)));
        constructAncestorPath(tree, parent);

        var candidates = engine.generateCandidates(parent, f1);
        // I-extension: x > 2 in F1 -> item 3: <(1)(2 3)>
        // S-extensions: items 1, 2, 3: <(1)(2)(1)>, <(1)(2)(2)>, <(1)(2)(3)>
        assertEquals(4, candidates.size());
        assertEquals(Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2), new Item(3))), candidates.get(0).pattern());
        assertEquals(Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2)), Itemset.of(new Item(1))), candidates.get(1).pattern());
        assertEquals(Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2)), Itemset.of(new Item(2))), candidates.get(2).pattern());
        assertEquals(Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2)), Itemset.of(new Item(3))), candidates.get(3).pattern());

        for (var c : candidates) {
            integrator.insertAcceptedChild(tree, parent, c);
        }
    }

    @Test
    void itemsetAndSequenceExtensionRemainDistinctNodesAndCandidates() {
        var tree = new PrefixTree();
        var f1 = List.of(new WeightedOnePattern(1, 10), new WeightedOnePattern(2, 20));
        integrator.initializeF1Roots(tree, f1);

        var p1 = Pattern.singleton(new Item(1));
        var iCandidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(new Item(1), new Item(2))),
                ExtensionType.I_EXTENSION,
                new Item(2));
        var sCandidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2))),
                ExtensionType.S_EXTENSION,
                new Item(2));

        var nodeI = integrator.insertAcceptedChild(tree, p1, iCandidate);
        var nodeS = integrator.insertAcceptedChild(tree, p1, sCandidate);

        assertNotNull(nodeI);
        assertNotNull(nodeS);
        assertFalse(nodeI == nodeS);
        assertFalse(nodeI.pattern().equals(nodeS.pattern()));
        assertEquals(ExtensionType.I_EXTENSION, nodeI.extensionType().orElseThrow());
        assertEquals(ExtensionType.S_EXTENSION, nodeS.extensionType().orElseThrow());
        assertSame(nodeI.parent().orElseThrow(), nodeS.parent().orElseThrow());
    }

    @Test
    void repeatedItemSExtensionPreservesParentAndEdge() {
        var tree = new PrefixTree();
        var f1 = List.of(new WeightedOnePattern(1, 10), new WeightedOnePattern(2, 20));
        integrator.initializeF1Roots(tree, f1);

        var parent = Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2)));
        constructAncestorPath(tree, parent);

        var repeatedCandidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2)), Itemset.of(new Item(1))),
                ExtensionType.S_EXTENSION,
                new Item(1));

        var node = integrator.insertAcceptedChild(tree, parent, repeatedCandidate);
        assertSame(tree.find(parent).orElseThrow(), node.parent().orElseThrow());
        assertEquals(ExtensionType.S_EXTENSION, node.extensionType().orElseThrow());
        assertEquals(new Item(1), node.pattern().lastItem().orElseThrow());
    }

    @Test
    void multiDigitF1PreservesNumericCandidateOrderAndSerializationTreeOrder() {
        var tree = new PrefixTree();
        var f1 = List.of(
                new WeightedOnePattern(10, 100),
                new WeightedOnePattern(2, 20),
                new WeightedOnePattern(1, 10));

        // Returned roots are strictly numeric ascending: 1, 2, 10
        var roots = integrator.initializeF1Roots(tree, f1);
        assertEquals(List.of(1, 2, 10), roots.stream().map(n -> n.pattern().lastItem().orElseThrow().id()).toList());

        // Generator order for <(1)> is strictly numeric
        var p1 = Pattern.singleton(new Item(1));
        var candidates = engine.generateCandidates(p1, f1);
        // I-extensions: 2, 10
        assertEquals(new Item(2), candidates.get(0).extensionItem());
        assertEquals(new Item(10), candidates.get(1).extensionItem());
        // S-extensions: 1, 2, 10
        assertEquals(new Item(1), candidates.get(2).extensionItem());
        assertEquals(new Item(2), candidates.get(3).extensionItem());
        assertEquals(new Item(10), candidates.get(4).extensionItem());

        // Tree serialization order for roots: root, <(1)>, <(10)>, <(2)>
        var preOrder = tree.preOrderPatterns();
        assertEquals(Pattern.empty(), preOrder.get(0));
        assertEquals(Pattern.singleton(new Item(1)), preOrder.get(1));
        assertEquals(Pattern.singleton(new Item(10)), preOrder.get(2));
        assertEquals(Pattern.singleton(new Item(2)), preOrder.get(3));
    }

    @Test
    void selectNoneInsertsNoCandidateNodes() {
        var tree = new PrefixTree();
        var f1 = List.of(new WeightedOnePattern(1, 10), new WeightedOnePattern(2, 20));
        integrator.initializeF1Roots(tree, f1);

        var p1 = Pattern.singleton(new Item(1));
        var candidates = engine.generateCandidates(p1, f1);
        assertFalse(candidates.isEmpty());

        int countBefore = tree.nodeCount();
        // Caller selects none
        for (var c : candidates) {
            assertTrue(tree.find(c.pattern()).isEmpty());
        }
        assertEquals(countBefore, tree.nodeCount());
    }

    @Test
    void selectAllInsertsAllCandidateNodes() {
        var tree = new PrefixTree();
        var f1 = List.of(new WeightedOnePattern(1, 10), new WeightedOnePattern(2, 20));
        integrator.initializeF1Roots(tree, f1);

        var p1 = Pattern.singleton(new Item(1));
        var candidates = engine.generateCandidates(p1, f1);

        int countBefore = tree.nodeCount();
        for (var c : candidates) {
            integrator.insertAcceptedChild(tree, p1, c);
        }

        assertEquals(countBefore + candidates.size(), tree.nodeCount());
        for (var c : candidates) {
            assertTrue(tree.find(c.pattern()).isPresent());
        }
    }

    @Test
    void selectAlternatingSubsetMaintainsExactSelectedAndUnselectedMembership() {
        var tree = new PrefixTree();
        var f1 = List.of(
                new WeightedOnePattern(1, 10),
                new WeightedOnePattern(2, 20),
                new WeightedOnePattern(3, 30));
        integrator.initializeF1Roots(tree, f1);

        var p1 = Pattern.singleton(new Item(1));
        var candidates = engine.generateCandidates(p1, f1);
        // Expect 5 candidates: I(2), I(3), S(1), S(2), S(3)
        assertEquals(5, candidates.size());

        int countBefore = tree.nodeCount();
        var selected = List.of(candidates.get(0), candidates.get(2), candidates.get(4));
        var unselected = List.of(candidates.get(1), candidates.get(3));

        for (var c : selected) {
            integrator.insertAcceptedChild(tree, p1, c);
        }

        assertEquals(countBefore + selected.size(), tree.nodeCount());
        for (var c : selected) {
            assertTrue(tree.find(c.pattern()).isPresent());
        }
        for (var c : unselected) {
            assertTrue(tree.find(c.pattern()).isEmpty());
        }
    }

    @Test
    void frozenTreeRejectsAcceptedChildInsertionWithoutMutation() {
        var tree = new PrefixTree();
        var f1 = List.of(new WeightedOnePattern(1, 10), new WeightedOnePattern(2, 20));
        integrator.initializeF1Roots(tree, f1);

        var p1 = Pattern.singleton(new Item(1));
        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(new Item(1), new Item(2))),
                ExtensionType.I_EXTENSION,
                new Item(2));

        tree.freeze();
        int countBefore = tree.nodeCount();

        assertThrows(IllegalStateException.class, () -> integrator.insertAcceptedChild(tree, p1, candidate));
        assertEquals(countBefore, tree.nodeCount());
        assertTrue(tree.find(candidate.pattern()).isEmpty());
    }

    @Test
    void invalidCandidateMismatchRejectsBeforeTreeMutation() {
        var tree = new PrefixTree();
        var f1 = List.of(new WeightedOnePattern(1, 10), new WeightedOnePattern(2, 20));
        integrator.initializeF1Roots(tree, f1);

        var p1 = Pattern.singleton(new Item(1));
        // Inconsistent candidate: extensionItem says 2, but pattern has item 3
        var bogusCandidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(new Item(1), new Item(3))),
                ExtensionType.I_EXTENSION,
                new Item(2));

        int countBefore = tree.nodeCount();
        assertThrows(IllegalArgumentException.class, () -> integrator.insertAcceptedChild(tree, p1, bogusCandidate));
        assertEquals(countBefore, tree.nodeCount());
        assertTrue(tree.find(bogusCandidate.pattern()).isEmpty());
    }

    // =========================================================================
    // Combinatorial Helpers
    // =========================================================================

    private static <T> List<List<T>> generateCombinations(List<T> list, int k) {
        var result = new ArrayList<List<T>>();
        combineHelper(list, k, 0, new ArrayList<T>(), result);
        return result;
    }

    private static <T> void combineHelper(List<T> list, int k, int start, List<T> current, List<List<T>> result) {
        if (current.size() == k) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < list.size(); i++) {
            current.add(list.get(i));
            combineHelper(list, k, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    private static <T> List<List<T>> generatePermutations(List<T> list) {
        var result = new ArrayList<List<T>>();
        permuteHelper(new ArrayList<>(list), 0, result);
        return result;
    }

    private static <T> void permuteHelper(List<T> list, int index, List<List<T>> result) {
        if (index == list.size()) {
            result.add(new ArrayList<>(list));
            return;
        }
        for (int i = index; i < list.size(); i++) {
            Collections.swap(list, index, i);
            permuteHelper(list, index + 1, result);
            Collections.swap(list, index, i);
        }
    }
}
