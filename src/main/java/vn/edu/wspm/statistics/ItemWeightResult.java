package vn.edu.wspm.statistics;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.OptionalDouble;
import java.util.TreeMap;

/** Immutable item weights and their maximum, minimum, and midpoint summary. */
public final class ItemWeightResult {

    private final NavigableMap<Integer, Double> itemWeights;
    private final Double maxWeight;
    private final Double minWeight;
    private final Double meanWeight;

    private ItemWeightResult(
            Map<Integer, Double> itemWeights,
            Double maxWeight,
            Double minWeight,
            Double meanWeight) {
        this.itemWeights = Collections.unmodifiableNavigableMap(new TreeMap<>(itemWeights));
        this.maxWeight = maxWeight;
        this.minWeight = minWeight;
        this.meanWeight = meanWeight;
    }

    static ItemWeightResult empty() {
        return new ItemWeightResult(Map.of(), null, null, null);
    }

    static ItemWeightResult of(
            Map<Integer, Double> itemWeights,
            double maxWeight,
            double minWeight,
            double meanWeight) {
        return new ItemWeightResult(itemWeights, maxWeight, minWeight, meanWeight);
    }

    /** Returns weights in ascending numeric item-identifier order. */
    public NavigableMap<Integer, Double> itemWeights() {
        return itemWeights;
    }

    public OptionalDouble weightFor(int itemId) {
        if (itemId < 0) {
            throw new IllegalArgumentException("item id must not be negative: " + itemId);
        }
        var weight = itemWeights.get(itemId);
        return weight == null ? OptionalDouble.empty() : OptionalDouble.of(weight);
    }

    public OptionalDouble maxWeight() {
        return optional(maxWeight);
    }

    public OptionalDouble minWeight() {
        return optional(minWeight);
    }

    public OptionalDouble meanWeight() {
        return optional(meanWeight);
    }

    private static OptionalDouble optional(Double value) {
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }
}
