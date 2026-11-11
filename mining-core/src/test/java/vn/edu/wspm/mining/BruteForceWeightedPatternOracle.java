package vn.edu.wspm.mining;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportMode;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;

/**
 * Test-only exhaustive weighted sequential-pattern oracle for tiny databases.
 *
 * <p>This implementation intentionally does not use the production miner, candidate generation,
 * Prefix Tree, Prime-block encoding, or production statistics/acceptance routines. It enumerates
 * every non-empty structural subsequence occurring in at least one input sequence, scans the
 * database directly for sequence support, and independently applies the global-MeanW condition.
 * It is complete only for strictly positive effective thresholds, under which a zero-support
 * pattern cannot be accepted.</p>
 */
final class BruteForceWeightedPatternOracle {

    static final long MAX_SUBSEQUENCE_CHOICES_PER_SEQUENCE = 1_000_000L;
    static final int MAX_UNIQUE_CANDIDATES = 1_000_000;

    private static final Comparator<Pattern> PATTERN_ORDER =
            Comparator.comparing(Pattern::serialize);

    Map<Pattern, Integer> mine(
            SequenceDatabase database, MinimumSupport minimumSupport) {
        Objects.requireNonNull(database, "database must not be null");
        Objects.requireNonNull(minimumSupport, "minimum support must not be null");

        // B18 policy: an empty database has no candidates and no MeanW to fabricate. In
        // particular, this early return does not change B7 percentage-resolution semantics.
        if (database.size() == 0) {
            return Map.of();
        }

        double effectiveThreshold = effectiveThreshold(minimumSupport, database.size());
        if (!(effectiveThreshold > 0.0)) {
            throw new IllegalArgumentException(
                    "brute-force oracle requires a strictly positive effective threshold; "
                            + "zero-threshold semantics remain unresolved by R6");
        }
        double meanWeight = calculateMeanWeight(database).orElseThrow();

        Set<Pattern> candidates = enumerateAllObservedPatterns(database);
        var accepted = new ArrayList<Map.Entry<Pattern, Integer>>();
        for (var candidate : candidates) {
            int support = countSupport(database, candidate);
            if (support * meanWeight >= effectiveThreshold) {
                accepted.add(Map.entry(candidate, support));
            }
        }
        accepted.sort(Map.Entry.comparingByKey(PATTERN_ORDER));

        var result = new LinkedHashMap<Pattern, Integer>();
        for (var entry : accepted) {
            result.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    Set<Pattern> enumerateAllObservedPatterns(SequenceDatabase database) {
        Objects.requireNonNull(database, "database must not be null");
        var candidates = new LinkedHashSet<Pattern>();
        for (var record : database.records()) {
            enumerateSubsequences(record.sequence(), candidates);
        }
        return Collections.unmodifiableSet(candidates);
    }

    List<Itemset> enumerateNonEmptySubsets(Itemset itemset) {
        Objects.requireNonNull(itemset, "itemset must not be null");
        var result = new ArrayList<Itemset>();
        enumerateSubsets(itemset.items(), 0, new ArrayList<>(), result);
        return List.copyOf(result);
    }

    boolean containsSubsequence(Sequence sequence, Pattern pattern) {
        Objects.requireNonNull(sequence, "sequence must not be null");
        Objects.requireNonNull(pattern, "pattern must not be null");
        if (pattern.isEmpty()) {
            return true;
        }

        int patternIndex = 0;
        for (var sequenceItemset : sequence.itemsets()) {
            if (sequenceItemset.items().containsAll(
                    pattern.itemsets().get(patternIndex).items())) {
                patternIndex++;
                if (patternIndex == pattern.size()) {
                    return true;
                }
            }
        }
        return false;
    }

    int countSupport(SequenceDatabase database, Pattern pattern) {
        Objects.requireNonNull(database, "database must not be null");
        Objects.requireNonNull(pattern, "pattern must not be null");
        int support = 0;
        for (var record : database.records()) {
            if (containsSubsequence(record.sequence(), pattern)) {
                support++;
            }
        }
        return support;
    }

    OptionalDouble calculateMeanWeight(SequenceDatabase database) {
        Objects.requireNonNull(database, "database must not be null");
        var occurrences = new TreeMap<Item, Long>();
        long totalDatabaseLength = 0L;

        for (var record : database.records()) {
            for (var itemset : record.sequence().itemsets()) {
                for (var item : itemset.items()) {
                    occurrences.merge(item, 1L, Long::sum);
                    totalDatabaseLength++;
                }
            }
        }
        if (occurrences.isEmpty()) {
            return OptionalDouble.empty();
        }

        double maxWeight = Double.NEGATIVE_INFINITY;
        double minWeight = Double.POSITIVE_INFINITY;
        for (long occurrenceCount : occurrences.values()) {
            double weight = (double) occurrenceCount / totalDatabaseLength;
            maxWeight = Math.max(maxWeight, weight);
            minWeight = Math.min(minWeight, weight);
        }
        return OptionalDouble.of((maxWeight + minWeight) / 2.0);
    }

    double effectiveThreshold(MinimumSupport minimumSupport, int numberOfSequences) {
        Objects.requireNonNull(minimumSupport, "minimum support must not be null");
        if (numberOfSequences < 0) {
            throw new IllegalArgumentException("number of sequences must not be negative");
        }
        if (minimumSupport.mode() == MinimumSupportMode.PERCENT) {
            return (minimumSupport.configuredValue() / 100.0) * numberOfSequences;
        }
        return minimumSupport.configuredValue();
    }

    private void enumerateSubsequences(Sequence sequence, Set<Pattern> destination) {
        ensureSequenceWithinLimit(sequence);
        var choicesByItemset = sequence.itemsets().stream()
                .map(this::enumerateNonEmptySubsets)
                .toList();
        enumerateItemsetChoices(choicesByItemset, 0, new ArrayList<>(), destination);
    }

    private void enumerateItemsetChoices(
            List<List<Itemset>> choicesByItemset,
            int itemsetIndex,
            List<Itemset> selected,
            Set<Pattern> destination) {
        if (itemsetIndex == choicesByItemset.size()) {
            if (!selected.isEmpty()) {
                var candidate = new Pattern(selected);
                if (!destination.contains(candidate)
                        && destination.size() == MAX_UNIQUE_CANDIDATES) {
                    throw new IllegalArgumentException(
                            "brute-force oracle candidate limit exceeded: "
                                    + MAX_UNIQUE_CANDIDATES);
                }
                destination.add(candidate);
            }
            return;
        }

        enumerateItemsetChoices(choicesByItemset, itemsetIndex + 1, selected, destination);
        for (var subset : choicesByItemset.get(itemsetIndex)) {
            selected.add(subset);
            enumerateItemsetChoices(choicesByItemset, itemsetIndex + 1, selected, destination);
            selected.removeLast();
        }
    }

    private void enumerateSubsets(
            List<Item> items,
            int itemIndex,
            List<Item> selected,
            List<Itemset> destination) {
        if (itemIndex == items.size()) {
            if (!selected.isEmpty()) {
                destination.add(new Itemset(selected));
            }
            return;
        }

        enumerateSubsets(items, itemIndex + 1, selected, destination);
        selected.add(items.get(itemIndex));
        enumerateSubsets(items, itemIndex + 1, selected, destination);
        selected.removeLast();
    }

    private void ensureSequenceWithinLimit(Sequence sequence) {
        long choicesIncludingEmpty = 1L;
        for (int ignored = 0; ignored < sequence.length(); ignored++) {
            if (choicesIncludingEmpty > (MAX_SUBSEQUENCE_CHOICES_PER_SEQUENCE + 1L) / 2L) {
                throw sequenceLimitExceeded(sequence);
            }
            choicesIncludingEmpty *= 2L;
        }
        if (choicesIncludingEmpty - 1L > MAX_SUBSEQUENCE_CHOICES_PER_SEQUENCE) {
            throw sequenceLimitExceeded(sequence);
        }
    }

    private IllegalArgumentException sequenceLimitExceeded(Sequence sequence) {
        return new IllegalArgumentException(
                "brute-force oracle sequence limit exceeded: length=" + sequence.length()
                        + " produces more than " + MAX_SUBSEQUENCE_CHOICES_PER_SEQUENCE
                        + " non-empty structural choices");
    }
}
