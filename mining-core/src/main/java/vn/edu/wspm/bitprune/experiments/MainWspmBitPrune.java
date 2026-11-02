package vn.edu.wspm.bitprune.experiments;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import vn.edu.wspm.bitprune.io.BitPruneOutputWriter;
import vn.edu.wspm.bitprune.measurement.BitPruneExecutionMetrics;
import vn.edu.wspm.bitprune.mining.BitPruneMiner;
import vn.edu.wspm.bitprune.result.BitPruneOutputProjection;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.SequenceDatabaseFormat;
import vn.edu.wspm.io.SequenceDatabaseParser;
import vn.edu.wspm.measurement.ExecutionMemorySampler;
import vn.edu.wspm.measurement.PhaseExecutionMetrics;
import vn.edu.wspm.model.SequenceDatabase;

/**
 * Standalone runner and six-scope runtime orchestrator for WSPM_BitPrune (C15.2).
 *
 * <p>Executes non-interactive mining runs across all six standardized measurement scopes:
 * <ol>
 *   <li>Scope 1 — Dataset I/O (file read and parse)</li>
 *   <li>Scope 2 — Preprocessing (database statistics, MeanW, F1, bitmaps, VOI, CMAP)</li>
 *   <li>Scope 3 — DFS Mining (candidate generation, filters, exact verification, tree integration)</li>
 *   <li>Scope 4 — Complete Algorithm (Scope 2 + Scope 3 + tree freeze & result construction)</li>
 *   <li>Scope 5 — Output I/O (physical file write via {@link BitPruneOutputWriter}, if export enabled)</li>
 *   <li>Scope 6 — Total Execution (covers Scopes 1-5 + coordination overhead; excludes console printing)</li>
 * </ol>
 * </p>
 *
 * <p>Path and configuration validation occurs strictly prior to sampler construction
 * and remains outside Total Execution measurement.</p>
 */
public final class MainWspmBitPrune {

    // Edit these constants to configure a reproducible non-interactive run.
    private static final Path DATASET_PATH =
            Path.of("src/test/resources/datasets/wspm/table1.spmf");

    private static final SequenceDatabaseFormat INPUT_FORMAT =
            SequenceDatabaseFormat.SPMF_SEQUENTIAL;

    private static final boolean MIN_SUP_IS_RELATIVE = true;

    private static final double MIN_SUP = 0.14;

    private static final boolean PRINT_PATTERNS = false;

    private static final boolean PRINT_STATISTICS = true;

    private static final boolean EXPORT_PATTERNS = false;

    private static final Path OUTPUT_PATH =
            Path.of("output/wspm-bitprune-patterns.txt");

    private MainWspmBitPrune() {
    }

    /**
     * Entry point for reproducible standalone execution of WSPM_BitPrune (C15.4).
     *
     * <p>Executes non-interactively using the source-configured constants above,
     * without terminal input, CLI parsing, or retention switches.</p>
     *
     * @param args command-line arguments (ignored)
     * @throws IOException if an I/O error occurs reading the dataset or writing the output
     */
    public static void main(String[] args) throws IOException {
        var configuration = new BitPruneRunConfiguration(
                DATASET_PATH,
                INPUT_FORMAT,
                MIN_SUP_IS_RELATIVE,
                MIN_SUP,
                PRINT_PATTERNS,
                PRINT_STATISTICS,
                EXPORT_PATTERNS,
                OUTPUT_PATH);

        execute(configuration, System.out);
    }

    /**
     * Executes WSPM_BitPrune standalone mining across the standardized six measurement scopes.
     *
     * @param configuration non-null run configuration
     * @return completed immutable execution run carrier
     * @throws NullPointerException if configuration is null
     * @throws IllegalArgumentException if dataset path is invalid or output path collides with input
     * @throws IOException if an I/O error occurs reading the dataset or writing the output
     */
    public static BitPruneExecutionRun execute(
            BitPruneRunConfiguration configuration) throws IOException {

        validatePaths(configuration);

        BitPruneExecutionRun executionRun;

        try (var sampler = new ExecutionMemorySampler()) {
            var totalStart = sampler.checkpoint();

            // Scope 1 — Dataset I/O
            var datasetIoStart = sampler.checkpoint();
            SequenceDatabase database = new SequenceDatabaseParser().parse(
                    configuration.datasetPath(),
                    configuration.inputFormat());
            var datasetIoEnd = sampler.checkpoint();

            var datasetIoMetrics = PhaseExecutionMetrics.between(
                    sampler,
                    datasetIoStart,
                    datasetIoEnd);

            MinimumSupport minimumSupport = configuration.toMinimumSupport();

            // Scopes 2, 3 and 4 are owned by BitPruneMiner.mineMeasured(...)
            var measuredRun = new BitPruneMiner().mineMeasured(
                    database,
                    minimumSupport,
                    sampler);

            var result = measuredRun.result();
            var algorithmMetrics = measuredRun.metrics();
            var metadata = measuredRun.metadata();

            // In-memory projection:
            // exactly once per successful execution;
            // inside Total Execution;
            // outside Output I/O.
            var projection = BitPruneOutputProjection.from(result);

            Optional<PhaseExecutionMetrics> outputIoMetrics = Optional.empty();

            if (configuration.exportPatterns()) {
                // Scope 5 — physical output only
                var outputIoStart = sampler.checkpoint();

                new BitPruneOutputWriter().write(
                        configuration.outputPath(),
                        projection);

                var outputIoEnd = sampler.checkpoint();

                outputIoMetrics = Optional.of(
                        PhaseExecutionMetrics.between(
                                sampler,
                                outputIoStart,
                                outputIoEnd));
            }

            // Scope 6 ends after output handling/projection and before sampler close.
            var totalEnd = sampler.checkpoint();

            var executionMetrics = new BitPruneExecutionMetrics(
                    datasetIoMetrics,
                    algorithmMetrics,
                    outputIoMetrics,
                    Math.max(0L, totalEnd.nanoTime() - totalStart.nanoTime()),
                    sampler.observedPeakBytes(totalStart, totalEnd),
                    sampler.samplingIntervalMillis());

            executionRun = new BitPruneExecutionRun(
                    result,
                    executionMetrics,
                    metadata,
                    projection);
        }

        return executionRun;
    }

    private static void validatePaths(BitPruneRunConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");

        Path input = configuration.datasetPath().toAbsolutePath().normalize();
        if (!Files.isRegularFile(input)) {
            throw new IllegalArgumentException(
                    "dataset file does not exist or is not a regular file: " + input);
        }

        if (configuration.exportPatterns()) {
            Path output = configuration.outputPath().toAbsolutePath().normalize();
            if (input.equals(output)) {
                throw new IllegalArgumentException(
                        "output path must not overwrite the input dataset: " + input);
            }
        }
    }

    /**
     * Executes WSPM_BitPrune standalone mining across the standardized six measurement scopes
     * and conditionally formats and prints statistics and accepted patterns to the provided console stream (C15.3).
     *
     * <p>Console formatting and printing occurs strictly after the core six-scope execution has completed,
     * the sampler has closed, and totally outside {@code totalExecutionNanos}.</p>
     *
     * @param configuration non-null run configuration
     * @param output non-null print stream for console reporting
     * @return completed immutable execution run carrier
     * @throws NullPointerException if configuration or output is null
     * @throws IllegalArgumentException if dataset path is invalid or output path collides with input
     * @throws IOException if an I/O error occurs reading the dataset or writing the output
     */
    public static BitPruneExecutionRun execute(
            BitPruneRunConfiguration configuration,
            PrintStream output) throws IOException {

        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(output, "output must not be null");

        BitPruneExecutionRun run = execute(configuration);

        if (configuration.printStatistics()) {
            printLines(output, summaryLines(configuration, run));
        }

        if (configuration.printPatterns()) {
            output.println("=== Accepted Patterns ===");
            printLines(output, run.projection().lines());
        }

        return run;
    }

    /**
     * Formats deterministic, locale-independent console summary lines for the four standardized
     * reporting sections: INPUT CONFIGURATION, MINING STATISTICS, MEMORY STATISTICS, and TIME STATISTICS.
     *
     * <p>Pure formatting method: performs no filesystem operations, no mining, no projection creation,
     * and no state mutations.</p>
     *
     * @param configuration non-null run configuration
     * @param executionRun non-null completed execution run carrier
     * @return immutable list of formatted summary lines
     * @throws NullPointerException if configuration or executionRun is null
     */
    static List<String> summaryLines(
            BitPruneRunConfiguration configuration,
            BitPruneExecutionRun executionRun) {

        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(executionRun, "executionRun must not be null");

        var result = executionRun.result();
        var metadata = executionRun.metadata();
        var execution = executionRun.metrics();
        var algorithm = execution.algorithm();
        var statistics = result.statistics();

        var lines = new ArrayList<String>();

        lines.add("========== INPUT CONFIGURATION ==========");
        lines.add(formatField("Algorithm", "WSPM_BitPrune"));
        lines.add(formatField("Dataset", configuration.datasetPath()));
        lines.add(formatField("Input Format", configuration.inputFormat()));
        lines.add(formatField("Number of Sequences", statistics.sequenceCount()));
        lines.add(formatField("Number of Distinct Items", metadata.distinctItemCount()));
        lines.add(formatField("Minimum Support Input", configuration.minimumSupport()
                + (configuration.minimumSupportIsRelative()
                        ? " (relative fraction)"
                        : " (absolute)")));
        lines.add(formatField("Exact Mode / Unit", metadata.minimumSupport().mode()));
        lines.add(formatField("Relative Fraction", metadata.minimumSupport().relativeMinimumSupport().stream()
                .mapToObj(MainWspmBitPrune::formatFiveDecimals)
                .findFirst()
                .orElse("N/A")));
        lines.add(formatField("Effective Minimum Support",
                formatFiveDecimals(metadata.minimumSupport().effectiveThreshold())));
        lines.add(formatField("Resolution Sequence Count", metadata.minimumSupport().numberOfSequences()));
        lines.add(formatField("MeanW", metadata.globalMeanWeight().stream()
                .mapToObj(MainWspmBitPrune::formatFiveDecimals)
                .findFirst()
                .orElse("N/A")));
        lines.add(formatField("Result Retention Model", "FINAL_SCIENTIFIC_RESULT"));
        lines.add(formatField("Print Patterns", configuration.printPatterns()));
        lines.add(formatField("Export Patterns", configuration.exportPatterns()));
        lines.add(formatField("Print Statistics", configuration.printStatistics()));
        lines.add(formatField("Output Path", configuration.outputPath()
                + (configuration.exportPatterns() ? "" : " (export disabled)")));

        lines.add("");
        lines.add("========== MINING STATISTICS ==========");
        lines.add(formatField("F1 Pattern Count (F1)", statistics.f1Count()));
        lines.add(formatField("Generated Extensions", statistics.generatedExtensions()));
        lines.add(formatField("Generated I-Extensions", statistics.generatedIExtensions()));
        lines.add(formatField("Generated S-Extensions", statistics.generatedSExtensions()));
        lines.add(formatField("Rejected Extensions", statistics.rejectedExtensions()));
        lines.add(formatField("Pruned by CMAP (Tier 1)", statistics.cmapRejected()));
        lines.add(formatField("Pruned by Bitmap Bound (Tier 2)", statistics.bitmapRejected()));
        lines.add(formatField("Rejected by Exact Verif (Tier 3)", statistics.exactRejected()));
        lines.add(formatField("Accepted Extensions", statistics.acceptedExtensions()));
        lines.add(formatField("Exact Verifications (Tier 3)", statistics.exactVerifications()));
        lines.add(formatField("Expanded DFS Nodes", statistics.expandedNodes()));
        lines.add(formatField("Number of Weighted Patterns", statistics.patternCount()));

        lines.add("");
        lines.add("========== MEMORY STATISTICS (MB) ==========");
        appendMemoryPhase(lines, "Dataset I/O", execution.datasetIo());
        lines.add("");
        appendMemoryPhase(lines, "Preprocessing", algorithm.preprocessing());
        lines.add("");
        appendMemoryPhase(lines, "DFS Mining", algorithm.dfsMining());
        lines.add("");
        if (execution.outputIo().isPresent()) {
            appendMemoryPhase(lines, "Output I/O", execution.outputIo().orElseThrow());
        } else {
            lines.add(formatField("Heap Before Output I/O", "N/A"));
            lines.add(formatField("Heap After Output I/O", "N/A"));
            lines.add(formatField("Peak Heap Output I/O", "N/A"));
        }
        lines.add("");
        lines.add(formatField("Peak Heap Complete Algorithm",
                formatMemory(algorithm.observedPeakHeapAlgorithmBytes())));
        lines.add(formatField("Peak Heap Total Execution",
                formatMemory(execution.observedPeakHeapTotalBytes())));
        lines.add(formatField("Memory Unit", "MB (1 MB = 1,000,000 bytes)"));
        lines.add(formatField("Peak Measurement", "Observed sampled Java heap used"));
        lines.add(formatField("Sampling Interval",
                execution.samplingIntervalMillis() + " ms"));

        lines.add("");
        lines.add("========== TIME STATISTICS (ms) ==========");
        lines.add(formatField("Dataset I/O Time",
                formatMilliseconds(execution.datasetIo().elapsedNanos())));
        lines.add(formatField("Preprocessing Time",
                formatMilliseconds(algorithm.preprocessing().elapsedNanos())));
        lines.add(formatField("DFS Mining Time",
                formatMilliseconds(algorithm.dfsMining().elapsedNanos())));
        lines.add(formatField("Complete Algorithm Time",
                formatMilliseconds(algorithm.completeAlgorithmNanos())));
        lines.add(formatField("Output I/O Time",
                formatMilliseconds(execution.outputIoNanos())));
        lines.add(formatField("Total Execution Time",
                formatMilliseconds(execution.totalExecutionNanos())));
        lines.add(formatField("Total Scope", "Excludes console formatting and printing"));

        return List.copyOf(lines);
    }

    private static void appendMemoryPhase(
            List<String> lines, String phaseName, PhaseExecutionMetrics metrics) {
        lines.add(formatField("Heap Before " + phaseName,
                formatMemory(metrics.heapBeforeBytes())));
        lines.add(formatField("Heap After " + phaseName,
                formatMemory(metrics.heapAfterBytes())));
        lines.add(formatField("Peak Heap " + phaseName,
                formatMemory(metrics.observedPeakHeapBytes())));
    }

    private static String formatField(String label, Object value) {
        int pad = Math.max(0, 31 - label.length());
        return label + " ".repeat(pad) + ": " + value;
    }

    private static String formatMemory(long bytes) {
        return String.format(Locale.ROOT, "%.3f", BitPruneExecutionMetrics.bytesToMegabytes(bytes));
    }

    private static String formatMilliseconds(long nanos) {
        return formatFiveDecimals(BigDecimal.valueOf(nanos).movePointLeft(6));
    }

    private static String formatFiveDecimals(double value) {
        return formatFiveDecimals(BigDecimal.valueOf(value));
    }

    private static String formatFiveDecimals(BigDecimal value) {
        return value.setScale(5, RoundingMode.HALF_UP).toPlainString();
    }

    private static void printLines(PrintStream output, List<String> lines) {
        lines.forEach(output::println);
    }
}

