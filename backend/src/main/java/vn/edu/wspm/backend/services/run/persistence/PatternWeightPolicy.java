package vn.edu.wspm.backend.services.run.persistence;

import java.util.Map;
import vn.edu.wspm.model.Pattern;

/** Owner-selected application projection, never the mining acceptance predicate. */
public final class PatternWeightPolicy {
    public static final String VERSION = "EQ2_EQ3_ARITHMETIC_MEAN_V1";

    private PatternWeightPolicy() {}

    public static double calculate(Pattern pattern, Map<Integer, Double> weights) {
        if (pattern == null || pattern.isEmpty() || weights == null) throw invalid();
        double sum = 0;
        for (var itemset : pattern.itemsets()) {
            double itemSum = 0;
            for (var item : itemset.items()) {
                Double weight = weights.get(item.id());
                if (item.id() <= 0 || weight == null || !Double.isFinite(weight) || weight <= 0 || weight > 1) throw invalid();
                itemSum += weight;
            }
            sum += itemSum / itemset.size();
        }
        double value = sum / pattern.size();
        if (!Double.isFinite(value) || value <= 0 || value > 1) throw invalid();
        return value;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Pattern weight requires a nonempty commercial pattern and finite item weights");
    }
}
