package vn.edu.wspm.bitprune.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.mining.WeightedOnePattern;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.tree.ExtensionType;

/**
 * Exhaustive small-universe and fixed-seed randomized differential evidence for
 * {@link BitPruneCandidateEngine} against an independent direct structural oracle.
 *
 * <p>The direct oracle constructs candidates directly from raw immutable lists without invoking
 * {@link BitPruneCandidateEngine}, {@code PatternExtensions}, baseline {@code CandidateEngine},
 * or PRISM joins.</p>
 */
class BitPruneCandidateEngineDifferentialTest {

    private static final long FIXED_SEED = 20261101L;
    private static final int RANDOM_TRIALS = 1500;

    private BitPruneCandidateEngine engine;

    @BeforeEach
    void setUp() {
        engine = new BitPruneCandidateEngine();
    }

    /**
     * Independent direct structural oracle built strictly from raw immutable lists without calling
     * {@link BitPruneCandidateEngine}, {@code PatternExtensions}, baseline {@code CandidateEngine},
     * or PRISM join operations.
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
            newItemsets.add(Itemset.of(extItem));
            Pattern child = new Pattern(newItemsets);
            expected.add(new BitPruneCandidateResult(child, ExtensionType.S_EXTENSION, extItem));
        }

        return List.copyOf(expected);
    }

    @Test
    void exhaustiveSmallUniverseDifferentialEvidence() {
        var universe = List.of(1, 2, 3, 4);
        var parentsCatalogue = buildCanonicalSmallUniverseCatalogue(universe);
        var f1Presentations = buildAllF1Presentations(universe);

        assertEquals(304, parentsCatalogue.size(), "Parents catalogue must contain exactly 304 patterns");
        assertEquals(64, f1Presentations.size(), "F1 presentations must contain exactly 64 ordered sequences");

        long len1 = f1Presentations.stream().filter(p -> p.size() == 1).count();
        long len2 = f1Presentations.stream().filter(p -> p.size() == 2).count();
        long len3 = f1Presentations.stream().filter(p -> p.size() == 3).count();
        long len4 = f1Presentations.stream().filter(p -> p.size() == 4).count();
        assertEquals(4, len1, "Length 1 presentations must be 4");
        assertEquals(12, len2, "Length 2 presentations must be 12");
        assertEquals(24, len3, "Length 3 presentations must be 24");
        assertEquals(24, len4, "Length 4 presentations must be 24");

        var uniquePresentations = new HashSet<>(f1Presentations.stream()
                .map(p -> p.stream().map(WeightedOnePattern::itemId).toList())
                .toList());
        assertEquals(64, uniquePresentations.size(), "All 64 presentations must be unique");

        int totalComparisons = 0;

        for (var parent : parentsCatalogue) {
            for (var f1 : f1Presentations) {
                var actual = engine.generateCandidates(parent, f1);
                var expected = directStructuralOracle(parent, f1);

                assertCandidatesMatch(parent, f1, expected, actual, "Exhaustive small-universe");
                totalComparisons++;
            }
        }

        assertEquals(19456, totalComparisons, "Total comparisons must be 304 x 64 = 19,456");
        System.out.printf(
                "C09.1 Exhaustive small-universe: %d parents x %d F1 presentations = %d comparisons, 0 mismatches%n",
                parentsCatalogue.size(), f1Presentations.size(), totalComparisons);
    }

    @Test
    void fixedSeedRandomizedDifferentialEvidence() {
        var random = new Random(FIXED_SEED);
        int totalTrials = RANDOM_TRIALS;

        for (int trial = 1; trial <= totalTrials; trial++) {
            var parent = generateRandomCanonicalPattern(random, 15);
            var f1 = generateRandomF1(random, 15);

            var actual = engine.generateCandidates(parent, f1);
            var expected = directStructuralOracle(parent, f1);

            assertCandidatesMatch(parent, f1, expected, actual,
                    "Fixed-seed randomized trial #" + trial + " (seed=" + FIXED_SEED + ")");
        }

        System.out.printf(
                "C09.1 Fixed-seed randomized evidence: %d trials (seed=%dL), 0 mismatches%n",
                totalTrials, FIXED_SEED);
    }

    @Test
    void baselineKnownStructuralPatternsOrderComparison() {
        // Parent <(1)>, F1 {1, 2, 3}
        var p1 = Pattern.of(Itemset.of(new Item(1)));
        var f1 = List.of(
                new WeightedOnePattern(1, 10),
                new WeightedOnePattern(2, 20),
                new WeightedOnePattern(3, 30));
        var r1 = engine.generateCandidates(p1, f1);
        assertEquals(5, r1.size());
        assertEquals("<(1 2)>", r1.get(0).pattern().serialize());
        assertEquals("<(1 3)>", r1.get(1).pattern().serialize());
        assertEquals("<(1)(1)>", r1.get(2).pattern().serialize());
        assertEquals("<(1)(2)>", r1.get(3).pattern().serialize());
        assertEquals("<(1)(3)>", r1.get(4).pattern().serialize());

        // Parent <(1 2)>, F1 {1, 2, 3}
        var p2 = Pattern.of(Itemset.of(new Item(1), new Item(2)));
        var r2 = engine.generateCandidates(p2, f1);
        assertEquals(4, r2.size());
        assertEquals("<(1 2 3)>", r2.get(0).pattern().serialize());
        assertEquals("<(1 2)(1)>", r2.get(1).pattern().serialize());
        assertEquals("<(1 2)(2)>", r2.get(2).pattern().serialize());
        assertEquals("<(1 2)(3)>", r2.get(3).pattern().serialize());

        // Parent <(1)(2)>, F1 {1, 2, 3}
        var p3 = Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2)));
        var r3 = engine.generateCandidates(p3, f1);
        assertEquals(4, r3.size());
        assertEquals("<(1)(2 3)>", r3.get(0).pattern().serialize());
        assertEquals("<(1)(2)(1)>", r3.get(1).pattern().serialize());
        assertEquals("<(1)(2)(2)>", r3.get(2).pattern().serialize());
        assertEquals("<(1)(2)(3)>", r3.get(3).pattern().serialize());
    }

    private static void assertCandidatesMatch(
            Pattern parent,
            List<WeightedOnePattern> f1,
            List<BitPruneCandidateResult> expected,
            List<BitPruneCandidateResult> actual,
            String context) {
        if (expected.size() != actual.size()) {
            fail(buildDiagnostic(parent, f1, expected, actual, -1,
                    context + " size mismatch: expected " + expected.size() + ", got " + actual.size()));
        }

        var uniqueActual = new HashSet<Pattern>();
        for (int i = 0; i < expected.size(); i++) {
            var expResult = expected.get(i);
            var actResult = actual.get(i);

            if (!uniqueActual.add(actResult.pattern())) {
                fail(buildDiagnostic(parent, f1, expected, actual, i,
                        context + " duplicate pattern generated in actual results: "
                                + actResult.pattern().serialize()));
            }

            if (!Objects.equals(expResult.pattern(), actResult.pattern())
                    || expResult.extensionType() != actResult.extensionType()
                    || !Objects.equals(expResult.extensionItem(), actResult.extensionItem())
                    || !Objects.equals(expResult.pattern().serialize(), actResult.pattern().serialize())) {
                fail(buildDiagnostic(parent, f1, expected, actual, i,
                        context + " candidate mismatch at index " + i));
            }
        }
    }

    private static String buildDiagnostic(
            Pattern parent,
            List<WeightedOnePattern> f1,
            List<BitPruneCandidateResult> expected,
            List<BitPruneCandidateResult> actual,
            int diffIndex,
            String message) {
        var sb = new StringBuilder();
        sb.append(message).append("\n");
        sb.append("Parent: ").append(parent.serialize()).append("\n");
        sb.append("F1: ").append(f1.stream().map(p -> String.valueOf(p.itemId())).toList()).append("\n");
        sb.append("Expected count=").append(expected.size())
                .append(", Actual count=").append(actual.size()).append("\n");
        if (diffIndex >= 0 && diffIndex < expected.size() && diffIndex < actual.size()) {
            var exp = expected.get(diffIndex);
            var act = actual.get(diffIndex);
            sb.append("First diff index: ").append(diffIndex).append("\n");
            sb.append("  Expected: pattern=").append(exp.pattern().serialize())
                    .append(", type=").append(exp.extensionType())
                    .append(", item=").append(exp.extensionItem().id()).append("\n");
            sb.append("  Actual:   pattern=").append(act.pattern().serialize())
                    .append(", type=").append(act.extensionType())
                    .append(", item=").append(act.extensionItem().id()).append("\n");
        }
        sb.append("Expected list: ").append(expected.stream().map(r -> r.pattern().serialize()).toList()).append("\n");
        sb.append("Actual list:   ").append(actual.stream().map(r -> r.pattern().serialize()).toList()).append("\n");
        return sb.toString();
    }

    private static List<Pattern> buildCanonicalSmallUniverseCatalogue(List<Integer> universe) {
        var catalogue = new ArrayList<Pattern>();

        // All non-empty canonical itemsets over universe {1, 2, 3, 4}
        var allItemsets = new ArrayList<Itemset>();
        int n = universe.size();
        for (int mask = 1; mask < (1 << n); mask++) {
            var items = new TreeSet<Item>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    items.add(new Item(universe.get(i)));
                }
            }
            allItemsets.add(new Itemset(items));
        }

        // 1. All 1-itemset patterns (size 1 to 4): 15 patterns
        for (var itemset : allItemsets) {
            catalogue.add(Pattern.of(itemset));
        }

        // 2. All 2-itemset patterns formed from combinations of allItemsets: 15 x 15 = 225 patterns
        for (var first : allItemsets) {
            for (var second : allItemsets) {
                catalogue.add(Pattern.of(first, second));
            }
        }

        // 3. 3-itemset patterns formed from singletons: 4 x 4 x 4 = 64 patterns
        for (int a : universe) {
            for (int b : universe) {
                for (int c : universe) {
                    catalogue.add(Pattern.of(
                            Itemset.of(new Item(a)),
                            Itemset.of(new Item(b)),
                            Itemset.of(new Item(c))));
                }
            }
        }

        return List.copyOf(catalogue);
    }

    private static List<List<WeightedOnePattern>> buildAllF1Presentations(List<Integer> universe) {
        var presentations = new ArrayList<List<WeightedOnePattern>>();
        var current = new ArrayList<Integer>();
        var used = new boolean[universe.size()];
        generatePermutations(universe, used, current, presentations);
        return List.copyOf(presentations);
    }

    private static void generatePermutations(
            List<Integer> universe,
            boolean[] used,
            List<Integer> current,
            List<List<WeightedOnePattern>> results) {
        if (!current.isEmpty()) {
            var f1 = new ArrayList<WeightedOnePattern>(current.size());
            for (int id : current) {
                f1.add(new WeightedOnePattern(id, id * 10));
            }
            results.add(List.copyOf(f1));
        }
        if (current.size() == universe.size()) {
            return;
        }
        for (int i = 0; i < universe.size(); i++) {
            if (!used[i]) {
                used[i] = true;
                current.add(universe.get(i));
                generatePermutations(universe, used, current, results);
                current.remove(current.size() - 1);
                used[i] = false;
            }
        }
    }

    private static Pattern generateRandomCanonicalPattern(Random random, int maxUniverseItem) {
        int numItemsets = 1 + random.nextInt(4); // 1 to 4 itemsets
        var itemsets = new ArrayList<Itemset>(numItemsets);

        for (int s = 0; s < numItemsets; s++) {
            int itemsetSize = 1 + random.nextInt(3); // 1 to 3 items
            var chosen = new TreeSet<Item>();
            while (chosen.size() < itemsetSize) {
                int itemId = 1 + random.nextInt(maxUniverseItem);
                chosen.add(new Item(itemId));
            }
            itemsets.add(new Itemset(chosen));
        }

        return new Pattern(itemsets);
    }

    private static List<WeightedOnePattern> generateRandomF1(Random random, int maxUniverseItem) {
        int f1Size = random.nextInt(11); // 0 to 10 items
        if (f1Size == 0) {
            return List.of();
        }

        var chosenIds = new ArrayList<Integer>();
        var universePool = new ArrayList<Integer>();
        for (int i = 1; i <= maxUniverseItem; i++) {
            universePool.add(i);
        }
        Collections.shuffle(universePool, random);

        for (int i = 0; i < f1Size; i++) {
            chosenIds.add(universePool.get(i));
        }

        var f1 = new ArrayList<WeightedOnePattern>(f1Size);
        for (int id : chosenIds) {
            int support = 1 + random.nextInt(100);
            f1.add(new WeightedOnePattern(id, support));
        }

        // Shuffle presentation order
        Collections.shuffle(f1, random);
        return List.copyOf(f1);
    }
}
