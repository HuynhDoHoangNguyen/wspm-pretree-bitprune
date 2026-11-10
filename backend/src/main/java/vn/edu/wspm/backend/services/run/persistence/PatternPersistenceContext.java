package vn.edu.wspm.backend.services.run.persistence;

import java.util.Map;
import java.util.Objects;
import vn.edu.wspm.backend.services.dataset.conversion.ProductItemDictionary;
import vn.edu.wspm.backend.services.run.PreparedRun;
import vn.edu.wspm.backend.services.run.engine.MiningEngineResult;
import vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission.Outcome;
import vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission.State;
import vn.edu.wspm.statistics.ItemStatisticsCalculator;
import vn.edu.wspm.statistics.ItemWeightCalculator;
import vn.edu.wspm.statistics.SequenceDatabaseStatistics;

/** Verified, immutable projection context; contains neither database nor display-text guesses. */
public final class PatternPersistenceContext {
    private final RunPersistenceContext identity;
    private final MiningEngineResult result;
    private final Map<Integer, Double> weights;
    private final Map<Integer, String> products;
    private final SequenceDatabaseStatistics statistics;
    private final double maxWeight, minWeight;

    private PatternPersistenceContext(Outcome outcome, PreparedRun fresh, ProductItemDictionary dictionary) {
        identity = outcome.persistenceContext(); result = outcome.result();
        statistics = new ItemStatisticsCalculator().calculate(fresh.engineRequest().database());
        var actual = new ItemWeightCalculator().calculate(statistics);
        var a = fresh.minimumSupport(); var b = result.minimumSupport();
        if (!identity.provenance().equals(RunProvenance.from(fresh))
                || result.algorithm() != fresh.engineRequest().algorithm()
                || a.mode() != b.mode() || a.numberOfSequences() != b.numberOfSequences()
                || statistics.numberOfSequences() != b.numberOfSequences()
                || !bits(a.configuredValue(), b.configuredValue())
                || !a.relativeMinimumSupport().equals(b.relativeMinimumSupport())
                || !bits(a.effectiveThreshold(), b.effectiveThreshold())
                || !actual.itemWeights().equals(fresh.weights().itemWeights())
                || !actual.maxWeight().equals(fresh.weights().maxWeight())
                || !actual.minWeight().equals(fresh.weights().minWeight())
                || !actual.meanWeight().equals(fresh.weights().meanWeight())
                || !bits(actual.meanWeight().orElseThrow(), result.globalMeanWeight())
                || actual.itemWeights().size() != result.distinctItemCount()
                || !actual.itemWeights().keySet().equals(dictionary.itemToProduct().keySet())) throw invalid();
        weights = Map.copyOf(actual.itemWeights()); products = Map.copyOf(dictionary.itemToProduct());
        maxWeight = actual.maxWeight().orElseThrow(); minWeight = actual.minWeight().orElseThrow();
    }

    /** Caller must obtain fresh through the verified artifact preparation boundary. */
    public static PatternPersistenceContext verified(Outcome outcome, PreparedRun fresh, ProductItemDictionary dictionary) {
        requireOutcome(outcome);
        return new PatternPersistenceContext(outcome, Objects.requireNonNull(fresh), Objects.requireNonNull(dictionary));
    }

    public static void requireOutcome(Outcome outcome) {
        if (outcome == null || outcome.state() != State.COMPLETED || !outcome.lifecycleConfirmed()
                || outcome.result() == null || outcome.metrics() == null || outcome.persistenceContext() == null
                || outcome.metrics().patternCount() != outcome.result().patternCount()
                || outcome.metrics().candidateCount() != outcome.result().statistics().candidateCount()) throw invalid();
    }

    public RunPersistenceContext identity() { return identity; }
    public MiningEngineResult result() { return result; }
    public Map<Integer, Double> weights() { return weights; }
    public Map<Integer, String> products() { return products; }
    public SequenceDatabaseStatistics statistics() { return statistics; }
    public double maxWeight() { return maxWeight; }
    public double minWeight() { return minWeight; }
    public static boolean bits(double a, double b) { return Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Pattern persistence context is inconsistent"); }
    @Override public String toString() { return "PatternPersistenceContext[runId=" + identity.runId() + "]"; }
}
