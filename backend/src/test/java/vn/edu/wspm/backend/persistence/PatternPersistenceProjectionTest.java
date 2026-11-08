package vn.edu.wspm.backend.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.backend.services.dataset.conversion.*;
import vn.edu.wspm.backend.services.run.PreparedRun;
import vn.edu.wspm.backend.services.run.engine.*;
import vn.edu.wspm.backend.services.run.execution.*;
import vn.edu.wspm.backend.services.run.persistence.*;
import vn.edu.wspm.config.*;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.measurement.PhaseExecutionMetrics;
import vn.edu.wspm.mining.WspmMiningStatistics;
import vn.edu.wspm.model.*;
import vn.edu.wspm.statistics.*;

/** Synthetic producer payloads test validation boundaries, not mining completeness. */
class PatternPersistenceProjectionTest {
    static final Itemset ONE = Itemset.of(new Item(1));
    static final Itemset TWO = Itemset.of(new Item(2));
    static Pattern pattern(Itemset... sets) { return Pattern.of(sets); }

    static PreparedRun input() throws Exception {
        var db = new SpmfSequenceParser().parse(new StringReader("1 2 3 10 -1 -2\n"));
        var config = MinimumSupport.absolute(.0001);
        var pub = new ConversionArtifactResult("snapshot", "attempt", "a".repeat(64),
                new ConversionArtifactResult.ArtifactFile("sequence", "b".repeat(64), 1),
                new ConversionArtifactResult.ArtifactFile("mapping", "c".repeat(64), 1),
                new ConversionArtifactResult.Statistics(1, 1, 0, 1, 4, 4, 4, 4, 4));
        return new PreparedRun(new MiningEngineRequest(MiningAlgorithm.WSPM_PRETREE, db, config),
                new MinimumSupportResolver().resolve(config, 1),
                new ItemWeightCalculator().calculate(new ItemStatisticsCalculator().calculate(db)),
                new PreparedRun.Binding(pub, "CSV", "projection test", "raw", "d".repeat(64)));
    }

    static ManagedRunExecutionAdmission.Outcome outcome(PreparedRun input, Map<Pattern, Integer> supports) {
        int count = supports.size();
        var phase = new PhaseExecutionMetrics(0, 0, 0, 0);
        var result = new MiningEngineResult(MiningAlgorithm.WSPM_PRETREE, supports, input.minimumSupport(), 4,
                input.weights().meanWeight().orElseThrow(),
                new MiningEngineResult.PreTreeStatistics(new WspmMiningStatistics(1, count, 0, 0, 0, 0, 0, 0, count, 0)),
                new MiningEngineResult.Metrics(phase, phase, 0, 0, 0));
        return new ManagedRunExecutionAdmission.Outcome("run", UUID.randomUUID(), ManagedRunExecutionAdmission.State.COMPLETED,
                result, null, true, RunMetricsCapture.from(result, 0, 0), RunPersistenceContext.from("run", input));
    }

    static PatternPersistenceContext context(PreparedRun input, Map<Pattern, Integer> supports) {
        return PatternPersistenceContext.verified(outcome(input, supports), input,
                new ProductItemDictionary(Map.of("p1", 1, "p2", 2, "p3", 3, "p10", 10)));
    }

    @Test void twoStageMeanDiffersFromFlattenedMeanAndRepeatedItemsContributePerItemset() {
        var p = pattern(Itemset.of(new Item(1), new Item(2)), Itemset.of(new Item(3)));
        assertEquals(.375, PatternWeightPolicy.calculate(p, Map.of(1, .125, 2, .375, 3, .5)));
        assertNotEquals(1.0 / 3, PatternWeightPolicy.calculate(p, Map.of(1, .125, 2, .375, 3, .5)));
        assertEquals(.125, PatternWeightPolicy.calculate(pattern(ONE, ONE), Map.of(1, .125)));
        assertEquals("EQ2_EQ3_ARITHMETIC_MEAN_V1", PatternWeightPolicy.VERSION);
    }

    @Test void invalidOrMissingWeightsAndEmptyOrNoncommercialPatternsAreRejected() {
        for (Double weight : List.of(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -.1, 1.1)) {
            assertThrows(IllegalArgumentException.class, () -> PatternWeightPolicy.calculate(pattern(ONE), Map.of(1, weight)));
        }
        assertThrows(IllegalArgumentException.class, () -> PatternWeightPolicy.calculate(pattern(ONE), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> PatternWeightPolicy.calculate(Pattern.empty(), Map.of(1, .5)));
        assertThrows(IllegalArgumentException.class, () -> PatternWeightPolicy.calculate(Pattern.singleton(new Item(0)), Map.of(0, .5)));
    }

    @Test void temporalIdentityExactFieldsHashesAndOrderingAreStable() throws Exception {
        var input = input();
        var temporal = pattern(ONE, TWO); var together = pattern(Itemset.of(new Item(1), new Item(2)));
        var supports = new LinkedHashMap<Pattern, Integer>(); supports.put(temporal, 1); supports.put(together, 1);
        var batch = PatternPersistenceProjector.project(context(input, supports));
        var reverse = new LinkedHashMap<Pattern, Integer>(); reverse.put(together, 1); reverse.put(temporal, 1);
        assertEquals(batch.rows(), PatternPersistenceProjector.project(context(input, reverse)).rows());
        assertEquals(2, batch.rows().stream().map(r -> r.hash()).distinct().count());
        for (var row : batch.rows()) {
            assertEquals(1, row.support()); assertEquals(1, row.ratio()); assertEquals(.25, row.weightedSupport());
            assertEquals(2, row.itemCount()); assertEquals(.25, row.weight());
            assertEquals(ConversionArtifactCodec.sha256(row.canonical().getBytes(java.nio.charset.StandardCharsets.UTF_8)), row.hash());
            assertEquals(row.canonical().equals("<(1)(2)>") ? 2 : 1, row.itemsetCount());
        }
        assertThrows(UnsupportedOperationException.class, () -> batch.rows().clear());
    }

    @Test void mismatchedDictionaryProvenanceAlgorithmResolutionOrSummaryCannotBecomeContext() throws Exception {
        var input = input(); var outcome = outcome(input, Map.of(pattern(ONE), 1));
        assertThrows(IllegalArgumentException.class, () -> PatternPersistenceContext.verified(outcome, input,
                new ProductItemDictionary(Map.of("p1", 1))));
        var changed = new PreparedRun(input.engineRequest(), input.minimumSupport(), input.weights(),
                new PreparedRun.Binding(input.binding().publication(), "CSV", "changed", "raw", "d".repeat(64)));
        var dictionary = new ProductItemDictionary(Map.of("p1", 1, "p2", 2, "p3", 3, "p10", 10));
        assertThrows(IllegalArgumentException.class, () -> PatternPersistenceContext.verified(outcome, changed, dictionary));
        var otherAlgorithm = new PreparedRun(new MiningEngineRequest(MiningAlgorithm.WSPM_BITPRUNE,
                input.engineRequest().database(), input.engineRequest().minimumSupport()), input.minimumSupport(), input.weights(), input.binding());
        assertThrows(IllegalArgumentException.class, () -> PatternPersistenceContext.verified(outcome, otherAlgorithm, dictionary));
        var config = MinimumSupport.absolute(.2);
        var otherResolution = new PreparedRun(new MiningEngineRequest(MiningAlgorithm.WSPM_PRETREE,
                input.engineRequest().database(), config), new MinimumSupportResolver().resolve(config, 1), input.weights(), input.binding());
        assertThrows(IllegalArgumentException.class, () -> PatternPersistenceContext.verified(outcome, otherResolution, dictionary));
        var original = outcome.result();
        var wrongSummary = new MiningEngineResult(original.algorithm(), original.supports(), original.minimumSupport(),
                original.distinctItemCount(), .3, original.statistics(), original.metrics());
        var wrongOutcome = new ManagedRunExecutionAdmission.Outcome(outcome.runId(), outcome.token(), outcome.state(), wrongSummary,
                null, true, RunMetricsCapture.from(wrongSummary, 0, 0), outcome.persistenceContext());
        assertThrows(IllegalArgumentException.class, () -> PatternPersistenceContext.verified(wrongOutcome, input, dictionary));
    }

    @Test void unconfirmedOutcomeAndMissingDictionaryItemCannotBePersisted() throws Exception {
        var input = input(); var good = outcome(input, Map.of(pattern(ONE), 1));
        var pending = new ManagedRunExecutionAdmission.Outcome(good.runId(), good.token(), good.state(), good.result(), null,
                false, good.metrics(), good.persistenceContext());
        assertThrows(IllegalArgumentException.class, () -> PatternPersistenceContext.requireOutcome(pending));
        assertThrows(IllegalArgumentException.class, () -> PatternPersistenceProjector.project(context(input,
                Map.of(Pattern.singleton(new Item(99)), 1))));
    }

    @Test void mysqlTextBoundaryIsMeasuredInUtf8BytesWithoutTruncation() throws Exception {
        var input = input();
        var sets = new ArrayList<>(Collections.nCopies(21844, ONE)); sets.set(0, Itemset.of(new Item(10)));
        var exact = new Pattern(sets);
        assertEquals(65535, exact.serialize().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertEquals(65535, PatternPersistenceProjector.project(context(input, Map.of(exact, 1))).rows().getFirst().canonical().length());
        sets.add(ONE);
        assertThrows(IllegalArgumentException.class, () -> PatternPersistenceProjector.project(context(input, Map.of(new Pattern(sets), 1))));
    }

    @Test void patternCountAndAggregateBudgetRejectWholeBatch() throws Exception {
        var input = input(); var supports = new LinkedHashMap<Pattern, Integer>();
        for (int i = 0; i <= PatternPersistenceProjector.MAX_PATTERNS; i++) {
            var sets = new ArrayList<Itemset>();
            for (int bit = 0; bit < 14; bit++) sets.add((i & (1 << bit)) == 0 ? ONE : TWO);
            supports.put(new Pattern(sets), 1);
        }
        assertThrows(IllegalArgumentException.class, () -> PatternPersistenceProjector.project(context(input, supports)));
        supports.clear();
        for (int i = 0; i < 140; i++) {
            var sets = new ArrayList<>(Collections.nCopies(21000, ONE));
            for (int bit = 0; bit < 8; bit++) if ((i & (1 << bit)) != 0) sets.set(bit, TWO);
            supports.put(new Pattern(sets), 1);
        }
        assertThrows(IllegalArgumentException.class, () -> PatternPersistenceProjector.project(context(input, supports)));
    }

    @Test void emptyResultProjectsEmptyImmutableBatch() throws Exception {
        assertTrue(PatternPersistenceProjector.project(context(input(), Map.of())).rows().isEmpty());
    }

    @Test void projectionRejectsAProducerPatternOutsideTheUnchangedAcceptancePredicate() throws Exception {
        var input = input(); var config = MinimumSupport.absolute(.3);
        var prepared = new PreparedRun(new MiningEngineRequest(MiningAlgorithm.WSPM_PRETREE, input.engineRequest().database(), config),
                new MinimumSupportResolver().resolve(config, 1), input.weights(), input.binding());
        assertThrows(IllegalArgumentException.class, () -> PatternPersistenceProjector.project(context(prepared, Map.of(pattern(ONE), 1))));
    }
}
