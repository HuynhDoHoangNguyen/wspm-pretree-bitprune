package vn.edu.wspm.experiments;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
        var result = MainWspmPreTree.run(
                configuration,
                new PrintStream(bytes, true, StandardCharsets.UTF_8));
        String output = bytes.toString(StandardCharsets.UTF_8);
        var statistics = result.statistics();

        assertTrue(output.contains("Minimum support input: 0.14 (relative fraction)"));
        assertTrue(output.contains("Configured minimum support: 14.0%"));
        assertTrue(output.contains("Effective minimum support: 0.98000"));
        assertTrue(output.contains("Sequence count: " + statistics.sequenceCount()));
        assertTrue(output.contains("F1 count: " + statistics.f1Count()));
        assertTrue(output.contains("Candidate count: " + statistics.candidateCount()));
        assertTrue(output.contains(
                "Accepted candidate count: " + statistics.acceptedCandidateCount()));
        assertTrue(output.contains(
                "Rejected candidate count: " + statistics.rejectedCandidateCount()));
        assertTrue(output.contains("I-candidate count: " + statistics.iCandidateCount()));
        assertTrue(output.contains("S-candidate count: " + statistics.sCandidateCount()));
        assertTrue(output.contains("Expanded node count: " + statistics.expandedNodeCount()));
        assertTrue(output.contains("Accepted pattern count: " + statistics.patternCount()));
        BigDecimal elapsedNanos = BigDecimal.valueOf(statistics.elapsedNanos());
        String expectedElapsedLine = "Miner elapsed time: "
                + fiveDecimals(elapsedNanos.movePointLeft(9))
                + " s ("
                + fiveDecimals(elapsedNanos.movePointLeft(6))
                + " ms)";
        assertTrue(MainWspmPreTree.summaryLines(configuration, result)
                .contains(expectedElapsedLine));
        assertFalse(output.contains("Elapsed time (ns):"));
    }

    private static String fiveDecimals(BigDecimal value) {
        return value.setScale(5, RoundingMode.HALF_UP).toPlainString();
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

        run(configuration);

        assertFalse(Files.exists(output));
    }

    @Test
    void enabledExportIsUtfEightCompleteDeterministicAndCannotOverwriteInput()
            throws IOException, URISyntaxException {
        Path output = temporaryDirectory.resolve("nested/results.txt");
        var configuration = tableOneConfiguration(false, false, true, output);

        var first = run(configuration);
        String expected = String.join("\n", MainWspmPreTree.patternLines(first)) + "\n";
        assertEquals(expected, Files.readString(output, StandardCharsets.UTF_8));

        var second = run(configuration);
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
