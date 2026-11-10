package vn.edu.wspm.backend.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.backend.services.dataset.conversion.ProductItemDictionary;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactResult;
import vn.edu.wspm.backend.services.run.PreparedRun;
import vn.edu.wspm.backend.services.run.engine.*;
import vn.edu.wspm.backend.services.run.execution.*;
import vn.edu.wspm.backend.services.run.persistence.*;
import vn.edu.wspm.backend.services.run.persistence.RunItemStatisticsProjector.*;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.mining.WspmMiningStatistics;
import vn.edu.wspm.statistics.*;

/** Synthetic producer context for projection limits; integration tests use verified real pipeline input. */
class RunItemStatisticsProjectionTest {
    static Batch project(String spmf) throws Exception {
        var seed = PatternPersistenceProjectionTest.input();
        var db = new SpmfSequenceParser().parse(new StringReader(spmf));
        var weights = new ItemWeightCalculator().calculate(new ItemStatisticsCalculator().calculate(db));
        var request = new MiningEngineRequest(seed.engineRequest().algorithm(), db, seed.engineRequest().minimumSupport());
        var oldPublication = seed.binding().publication();
        var lengths = db.records().stream().mapToInt(record -> record.sequence().itemsets().stream().mapToInt(set -> set.size()).sum()).summaryStatistics();
        long single = db.records().stream().filter(record -> record.sequence().size() == 1).count();
        long sets = db.records().stream().mapToLong(record -> record.sequence().size()).sum();
        var publication = new ConversionArtifactResult(oldPublication.snapshotId(), oldPublication.attemptId(), oldPublication.validationSha256(),
                oldPublication.sequence(), oldPublication.mapping(), new ConversionArtifactResult.Statistics(db.size(), single, db.size() - single, sets,
                weights.itemWeights().size(), lengths.getSum(), lengths.getMin(), lengths.getMax(), lengths.getAverage()));
        var binding = new PreparedRun.Binding(publication, seed.binding().sourceType(), seed.binding().sourceName(),
                seed.binding().rawReference(), seed.binding().rawSha256());
        var input = new PreparedRun(request, new MinimumSupportResolver().resolve(request.minimumSupport(), db.size()), weights, binding);
        var old = PatternPersistenceProjectionTest.outcome(seed, Map.of()); var result = old.result();
        var actual = new MiningEngineResult(result.algorithm(), Map.of(), input.minimumSupport(), weights.itemWeights().size(),
                weights.meanWeight().orElseThrow(), new MiningEngineResult.PreTreeStatistics(
                        new WspmMiningStatistics(db.size(), 0, 0, 0, 0, 0, 0, 0, 0, 0)), result.metrics());
        var outcome = new ManagedRunExecutionAdmission.Outcome(old.runId(), old.token(), old.state(), actual, null, true,
                RunMetricsCapture.from(actual, 0, 0), RunPersistenceContext.from(old.runId(), input));
        var dictionary = new HashMap<String, Integer>(); weights.itemWeights().keySet().forEach(i -> dictionary.put("p" + i, i));
        var context = PatternPersistenceContext.verified(outcome, input, new ProductItemDictionary(dictionary));
        return RunItemStatisticsProjector.project(TemporalPatternProjector.project(PatternPersistenceProjector.project(context)));
    }

    @Test void repeatedItemsDistinguishOccurrencesFromSequenceSupportAndIncludeAllItemsWithNoPatterns() throws Exception {
        var batch = project("1 2 -1 1 -1 3 -1 -2\n1 3 -1 -2\n");
        assertEquals(List.of(new Row(1, "p1", 3, 2, .5), new Row(2, "p2", 1, 1, 1.0 / 6),
                new Row(3, "p3", 2, 2, 2.0 / 6)), batch.rows());
        assertTrue(batch.temporal().parents().rows().isEmpty());
        assertEquals(6, batch.temporal().parents().context().statistics().totalDatabaseLength());
        assertThrows(UnsupportedOperationException.class, () -> batch.rows().clear());
        assertThrows(UnsupportedOperationException.class, () -> batch.temporal().parents().context().statistics().itemStatistics().clear());
    }

    @Test void exactItemCountBoundaryAndOverflowAreCheckedBeforeTransaction() throws Exception {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= RunItemStatisticsProjector.MAX_ITEMS; i++) text.append(i).append(' ');
        assertEquals(10_000, project(text + "-1 -2\n").rows().size());
        text.append(10_001).append(' ');
        assertThrows(IllegalArgumentException.class, () -> project(text + "-1 -2\n"));
    }

    @Test void commercialProjectionRejectsCoreItemZero() {
        assertThrows(IllegalArgumentException.class, () -> project("0 1 -1 -2\n"));
    }

    @Test void unicodeLimitsCountCodePointsAndNeverTruncateOrNormalize() {
        var row = new StoredRow(new Row(1, "p", 1, 1, 1), "😀".repeat(200), "😀".repeat(80));
        assertEquals(List.of(row), RunItemStatisticsProjector.validateStored(List.of(row)));
        assertThrows(IllegalArgumentException.class, () -> RunItemStatisticsProjector.validateStored(
                List.of(new StoredRow(row.statistics(), row.name() + "a", row.sku()))));
        assertThrows(IllegalArgumentException.class, () -> RunItemStatisticsProjector.validateStored(
                List.of(new StoredRow(row.statistics(), row.name(), row.sku() + "a"))));
        var spaced = new StoredRow(row.statistics(), "  Tên e\u0301 😀  ", " SKU ");
        assertEquals(spaced, RunItemStatisticsProjector.validateStored(List.of(spaced)).getFirst());
    }

    @Test void nullBlankAndMalformedUnicodeAreRejected() {
        var row = new Row(1, "p", 1, 1, 1);
        for (String text : Arrays.asList(null, "", "  ", "\ud800", "\udc00")) {
            assertThrows(IllegalArgumentException.class, () -> RunItemStatisticsProjector.validateStored(List.of(new StoredRow(row, text, "sku"))));
            assertThrows(IllegalArgumentException.class, () -> RunItemStatisticsProjector.validateStored(List.of(new StoredRow(row, "name", text))));
        }
    }

    @Test void exactUtf8AccountingBoundaryAndRowLimitAreEnforced() {
        var heavy = new StoredRow(new Row(1, "p".repeat(40), 1, 1, 1), "😀".repeat(200), "😀".repeat(80));
        var rows = new ArrayList<>(Collections.nCopies(6204, heavy)); // 6204 * (192 + 40 + 800 + 320)
        var last = new StoredRow(new Row(1, "p123", 1, 1, 1), "😀".repeat(150) + "abc", "s"); // 800 bytes
        rows.add(last);
        assertEquals(8_388_608L, 6204L * 1352 + 800);
        assertEquals(6205, RunItemStatisticsProjector.validateStored(rows).size());
        rows.set(rows.size() - 1, new StoredRow(last.statistics(), last.name() + "a", last.sku()));
        assertThrows(IllegalArgumentException.class, () -> RunItemStatisticsProjector.validateStored(rows));
        assertThrows(IllegalArgumentException.class, () -> RunItemStatisticsProjector.validateStored(Collections.nCopies(10_001, heavy)));
    }
}
