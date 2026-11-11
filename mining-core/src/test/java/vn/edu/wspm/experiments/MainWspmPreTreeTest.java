package vn.edu.wspm.experiments;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.wspm.config.MinimumSupportMode;
import vn.edu.wspm.io.SequenceDatabaseFormat;
import vn.edu.wspm.measurement.WspmExecutionMetrics;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;

class MainWspmPreTreeTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void relativePointFourteenConvertsToFourteenPercent() {
        var minimumSupport = MainWspmPreTree.toMinimumSupport(0.14, true);

        assertEquals(MinimumSupportMode.PERCENT, minimumSupport.mode());
        assertEquals(14.0, minimumSupport.configuredValue());
    }

    @Test
    void absolutePointNineEightRemainsAbsolute() {
        var minimumSupport = MainWspmPreTree.toMinimumSupport(0.98, false);

        assertEquals(MinimumSupportMode.ABSOLUTE, minimumSupport.mode());
        assertEquals(0.98, minimumSupport.configuredValue());
    }

    @Test
    void spmfPipelineReproducesCompleteTableOneMap()
            throws IOException, URISyntaxException {
        var result = run(tableOneConfiguration(false, false, false, unusedOutput()));

        assertEquals(tableOneOracle(), result.supports());
        assertEquals(9, result.statistics().patternCount());
        assertEquals(3, result.statistics().f1Count());
        assertEquals(6, result.statistics().acceptedCandidateCount());
    }

    @Test
    void fimiPipelineAllowsItemsetExtensionWithoutInventingTemporalSupport()
            throws IOException {
        Path input = temporaryDirectory.resolve("mini.fimi");
        Files.writeString(input, "1 2 3\n1 2\n1 3\n", StandardCharsets.UTF_8);
        var configuration = new MainWspmPreTree.RunConfiguration(
                input,
                SequenceDatabaseFormat.FIMI_TRANSACTION,
                false,
                0.5,
                false,
                false,
                false,
                unusedOutput());

        var result = run(configuration);

        assertEquals(3, result.statistics().sequenceCount());
        assertEquals(2, result.support(pattern(itemset(1, 2))));
        assertFalse(result.supports().containsKey(pattern(itemset(1), itemset(2))));
        assertTrue(result.statistics().iCandidateCount() > 0);
        assertTrue(result.statistics().sCandidateCount() > 0);
    }

    @Test
    void summaryUsesProductionStatisticsValues() throws IOException, URISyntaxException {
        var configuration = tableOneConfiguration(false, true, false, unusedOutput());
        var bytes = new ByteArrayOutputStream();
        var executionRun = MainWspmPreTree.execute(
                configuration,
                new PrintStream(bytes, true, StandardCharsets.UTF_8));
        String output = bytes.toString(StandardCharsets.UTF_8);
        var result = executionRun.miningResult();
        var statistics = result.statistics();

        assertTrue(output.contains("========== INPUT CONFIGURATION =========="));
        assertTrue(output.contains("Minimum Support Input          : 0.14 (relative fraction)"));
        assertTrue(output.contains("Effective Minimum Support      : 0.98000"));
        assertTrue(output.contains("Number of Sequences            : " + statistics.sequenceCount()));
        assertTrue(output.contains("Number of Distinct Items       : 8"));
        assertTrue(output.contains("Result Retention Policy        : SUPPORT_ONLY"));
        assertTrue(output.contains("========== MINING STATISTICS =========="));
        assertTrue(output.contains("Generated Candidates           : "
                + statistics.candidateCount()));
        assertTrue(output.contains("Accepted Candidates            : "
                + statistics.acceptedCandidateCount()));
        assertTrue(output.contains("Rejected Candidates            : "
                + statistics.rejectedCandidateCount()));
        assertTrue(output.contains("Number of Weighted Patterns    : "
                + statistics.patternCount()));
        assertTrue(output.contains("========== MEMORY STATISTICS (MB) =========="));
        assertTrue(output.contains("Heap Before Dataset I/O"));
        assertTrue(output.contains("Peak Heap Algorithm"));
        assertTrue(output.contains("Peak Heap Total Execution"));
        assertTrue(output.contains("Heap Before Output I/O         : N/A"));
        assertTrue(output.contains("Memory Unit                    : MB (1 MB = 1,000,000 bytes)"));
        assertTrue(output.contains("Peak Measurement               : Observed sampled Java heap used"));
        assertTrue(output.contains("========== TIME STATISTICS (ms) =========="));
        assertTrue(output.contains("Dataset I/O Time"));
        assertTrue(output.contains("Preprocessing Time"));
        assertTrue(output.contains("DFS Mining Time"));
        assertTrue(output.contains("Algorithm Time"));
        assertTrue(output.contains("Output I/O Time                : 0.00000"));
        assertTrue(output.contains("Total Scope                    : Excludes console printing"));
        assertEquals(9, result.statistics().patternCount());
        assertExecutionInvariants(executionRun.metrics());
    }

    @Test
    void patternOutputContainsEveryAcceptedPatternExactlyOnceInResultOrder()
            throws IOException, URISyntaxException {
        var configuration = tableOneConfiguration(true, false, false, unusedOutput());
        var bytes = new ByteArrayOutputStream();
        var result = MainWspmPreTree.run(
                configuration,
                new PrintStream(bytes, true, StandardCharsets.UTF_8));

        var actualLines = bytes.toString(StandardCharsets.UTF_8).lines().toList();
        var expectedLines = new java.util.ArrayList<String>();
        expectedLines.add("=== Accepted Patterns ===");
        expectedLines.addAll(MainWspmPreTree.patternLines(result));

        assertEquals(expectedLines, actualLines);
        assertEquals(result.statistics().patternCount(), actualLines.size() - 1);
        assertEquals(actualLines.size(), actualLines.stream().distinct().count());
    }

    @Test
    void disabledExportDoesNotCreateOutput() throws IOException, URISyntaxException {
        Path output = temporaryDirectory.resolve("disabled/results.txt");
        var configuration = tableOneConfiguration(false, false, false, output);

        var executionRun = execute(configuration);

        assertFalse(Files.exists(output));
        assertTrue(executionRun.metrics().outputIo().isEmpty());
        assertEquals(0L, executionRun.metrics().outputIoNanos());
    }

    @Test
    void enabledExportIsUtfEightCompleteDeterministicAndCannotOverwriteInput()
            throws IOException, URISyntaxException {
        Path output = temporaryDirectory.resolve("nested/results.txt");
        var configuration = tableOneConfiguration(false, false, true, output);

        var firstRun = execute(configuration);
        var first = firstRun.miningResult();
        String expected = String.join("\n", MainWspmPreTree.patternLines(first)) + "\n";
        assertEquals(expected, Files.readString(output, StandardCharsets.UTF_8));
        assertTrue(firstRun.metrics().outputIo().isPresent());
        assertExecutionInvariants(firstRun.metrics());

        var second = execute(configuration).miningResult();
        assertEquals(MainWspmPreTree.patternLines(first), MainWspmPreTree.patternLines(second));
        assertEquals(expected, Files.readString(output, StandardCharsets.UTF_8));

        Path input = tableOnePath();
        var unsafe = tableOneConfiguration(false, false, true, input);
        var error = assertThrows(IllegalArgumentException.class, () -> run(unsafe));
        assertTrue(error.getMessage().contains("must not overwrite the input dataset"));
        assertTrue(Files.readString(input, StandardCharsets.UTF_8).contains("-1 -2"));
    }

    @Test
    void missingDatasetFailsClearlyWithoutCreatingData() {
        Path missing = temporaryDirectory.resolve("missing/input.spmf");
        var configuration = new MainWspmPreTree.RunConfiguration(
                missing,
                SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                true,
                0.14,
                false,
                false,
                false,
                unusedOutput());

        var error = assertThrows(IllegalArgumentException.class, () -> run(configuration));

        assertTrue(error.getMessage().contains("dataset file does not exist"));
        assertTrue(error.getMessage().contains(missing.toAbsolutePath().normalize().toString()));
        assertFalse(Files.exists(missing));
    }

    private static vn.edu.wspm.mining.WspmMiningResult run(
            MainWspmPreTree.RunConfiguration configuration) throws IOException {
        return MainWspmPreTree.run(
                configuration,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    private static WspmExecutionRun execute(
            MainWspmPreTree.RunConfiguration configuration) throws IOException {
        return MainWspmPreTree.execute(
                configuration,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    private static void assertExecutionInvariants(WspmExecutionMetrics execution) {
        var algorithm = execution.algorithm();
        assertTrue(execution.datasetIo().elapsedNanos() >= 0L);
        assertTrue(algorithm.preprocessing().elapsedNanos() >= 0L);
        assertTrue(algorithm.dfsMining().elapsedNanos() >= 0L);
        assertTrue(algorithm.algorithmNanos() >= 0L);
        assertTrue(execution.totalExecutionNanos() >= 0L);
        assertTrue(algorithm.observedPeakHeapAlgorithmBytes()
                >= algorithm.preprocessing().observedPeakHeapBytes());
        assertTrue(algorithm.observedPeakHeapAlgorithmBytes()
                >= algorithm.dfsMining().observedPeakHeapBytes());
        assertTrue(execution.observedPeakHeapTotalBytes()
                >= algorithm.observedPeakHeapAlgorithmBytes());
        assertTrue(execution.observedPeakHeapTotalBytes()
                >= execution.datasetIo().observedPeakHeapBytes());
    }

    private static MainWspmPreTree.RunConfiguration tableOneConfiguration(
            boolean printPatterns,
            boolean printStatistics,
            boolean exportPatterns,
            Path outputPath) throws URISyntaxException {
        return new MainWspmPreTree.RunConfiguration(
                tableOnePath(),
                SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                true,
                0.14,
                printPatterns,
                printStatistics,
                exportPatterns,
                outputPath);
    }

    private static Path tableOnePath() throws URISyntaxException {
        return Path.of(requireNonNull(MainWspmPreTreeTest.class.getResource(
                "/datasets/wspm/table1.spmf")).toURI());
    }

    private Path unusedOutput() {
        return temporaryDirectory.resolve("unused.txt");
    }

    private static Map<Pattern, Integer> tableOneOracle() {
        var result = new LinkedHashMap<Pattern, Integer>();
        result.put(pattern(itemset(1)), 7);
        result.put(pattern(itemset(2)), 7);
        result.put(pattern(itemset(3)), 6);
        result.put(pattern(itemset(1), itemset(1)), 6);
        result.put(pattern(itemset(1), itemset(2)), 7);
        result.put(pattern(itemset(1, 2)), 7);
        result.put(pattern(itemset(1), itemset(3)), 6);
        result.put(pattern(itemset(2), itemset(3)), 6);
        result.put(pattern(itemset(1, 2), itemset(3)), 6);
        return Map.copyOf(result);
    }

    private static Pattern pattern(Itemset... itemsets) {
        return Pattern.of(itemsets);
    }

    private static Itemset itemset(int... ids) {
        return new Itemset(java.util.Arrays.stream(ids).mapToObj(Item::new).toList());
    }
}
