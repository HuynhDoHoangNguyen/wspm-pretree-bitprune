package vn.edu.wspm.experiments;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.SequenceDatabaseFormat;
import vn.edu.wspm.io.SequenceDatabaseParser;
import vn.edu.wspm.measurement.ExecutionMemorySampler;
import vn.edu.wspm.measurement.PhaseExecutionMetrics;
import vn.edu.wspm.measurement.WspmExecutionMetrics;
import vn.edu.wspm.mining.ResultRetentionPolicy;
import vn.edu.wspm.mining.WspmMiningResult;
import vn.edu.wspm.mining.WspmPreTreeMiner;

/** Standalone source-configured runner for the WSPM_PreTree baseline. */
public final class MainWspmPreTree {

        private static final Path DATASET_PATH = Path.of("src/test/resources/datasets/wspm/table1.spmf");
        private static final SequenceDatabaseFormat INPUT_FORMAT = SequenceDatabaseFormat.SPMF_SEQUENTIAL;

        // true: MIN_SUP is a relative fraction in [0, 1]; false: it is absolute.
        private static final boolean MIN_SUP_IS_RELATIVE = true;
        private static final double MIN_SUP = 0.14;

        private static final ResultRetentionPolicy RESULT_RETENTION = ResultRetentionPolicy.SUPPORT_ONLY;

        private static final boolean PRINT_PATTERNS = true;
        private static final boolean PRINT_STATISTICS = true;
        private static final boolean EXPORT_PATTERNS = false;
        private static final Path OUTPUT_PATH = Path.of("output/wspm-pretree-patterns.txt");

        private MainWspmPreTree() {
        }

        public static void main(String[] args) throws IOException {
                var configuration = new RunConfiguration(
                                DATASET_PATH,
                                INPUT_FORMAT,
                                MIN_SUP_IS_RELATIVE,
                                MIN_SUP,
                                RESULT_RETENTION,
                                PRINT_PATTERNS,
                                PRINT_STATISTICS,
                                EXPORT_PATTERNS,
                                OUTPUT_PATH);
                execute(configuration, System.out);
        }

        static WspmMiningResult run(
                        RunConfiguration configuration, PrintStream output) throws IOException {
                return execute(configuration, output).miningResult();
        }

        static WspmExecutionRun execute(
                        RunConfiguration configuration, PrintStream output) throws IOException {
                Objects.requireNonNull(configuration, "run configuration must not be null");
                Objects.requireNonNull(output, "console output must not be null");
                validatePaths(configuration);

                WspmExecutionRun executionRun;
                List<String> acceptedPatternLines;
                try (var sampler = new ExecutionMemorySampler()) {
                        var totalStart = sampler.checkpoint();

                        var datasetIoStart = sampler.checkpoint();
                        var database = new SequenceDatabaseParser().parse(
                                        configuration.datasetPath(), configuration.inputFormat());
                        var datasetIoEnd = sampler.checkpoint();
                        var datasetIoMetrics = PhaseExecutionMetrics.between(
                                        sampler, datasetIoStart, datasetIoEnd);

                        MinimumSupport minimumSupport = toMinimumSupport(
                                        configuration.minimumSupport(), configuration.minimumSupportIsRelative());
                        var measuredMining = new WspmPreTreeMiner()
                                        .mineMeasured(database, minimumSupport, sampler, configuration.resultRetention());
                        var result = measuredMining.result();
                        acceptedPatternLines = patternLines(result);

                        Optional<PhaseExecutionMetrics> outputIoMetrics = Optional.empty();
                        if (configuration.exportPatterns()) {
                                var outputIoStart = sampler.checkpoint();
                                exportPatterns(configuration.outputPath(), acceptedPatternLines);
                                var outputIoEnd = sampler.checkpoint();
                                outputIoMetrics = Optional.of(PhaseExecutionMetrics.between(
                                                sampler, outputIoStart, outputIoEnd));
                        }

                        var totalEnd = sampler.checkpoint();
                        var executionMetrics = new WspmExecutionMetrics(
                                        datasetIoMetrics,
                                        measuredMining.metrics(),
                                        outputIoMetrics,
                                        Math.max(0L, totalEnd.nanoTime() - totalStart.nanoTime()),
                                        sampler.observedPeakBytes(totalStart, totalEnd),
                                        sampler.samplingIntervalMillis());
                        executionRun = new WspmExecutionRun(result, executionMetrics);
                }

                if (configuration.printStatistics()) {
                        printLines(output, summaryLines(configuration, executionRun));
                }
                if (configuration.printPatterns()) {
                        output.println("=== Accepted Patterns ===");
                        printLines(output, acceptedPatternLines);
                }
                return executionRun;
        }

        static MinimumSupport toMinimumSupport(double value, boolean relative) {
                if (!Double.isFinite(value)) {
                        throw new IllegalArgumentException("minimum support must be finite");
                }
                if (relative) {
                        if (value < 0.0 || value > 1.0) {
                                throw new IllegalArgumentException(
                                                "relative minimum support must be between 0 and 1 inclusive");
                        }
                        // Preserve the source-configured decimal fraction (for example 0.14 -> 14.0)
                        // without introducing the binary multiplication artifact 14.000000000000002.
                        double percentage = BigDecimal.valueOf(value).movePointRight(2).doubleValue();
                        return MinimumSupport.percentage(percentage);
                }
                return MinimumSupport.absolute(value);
        }

        static List<String> summaryLines(
                        RunConfiguration configuration, WspmExecutionRun executionRun) {
                Objects.requireNonNull(configuration, "run configuration must not be null");
                Objects.requireNonNull(executionRun, "execution run must not be null");
                var result = executionRun.miningResult();
                var execution = executionRun.metrics();
                var algorithm = execution.algorithm();
                var statistics = result.statistics();
                var lines = new ArrayList<String>();
                lines.add("========== INPUT CONFIGURATION ==========");
                lines.add("Algorithm                      : WSPM_PreTree");
                lines.add("Dataset                        : " + configuration.datasetPath());
                lines.add("Input Format                   : " + configuration.inputFormat());
                lines.add("Number of Sequences            : " + statistics.sequenceCount());
                lines.add("Number of Distinct Items       : " + result.distinctItemCount());
                lines.add("Minimum Support Input          : " + configuration.minimumSupport()
                                + (configuration.minimumSupportIsRelative()
                                                ? " (relative fraction)"
                                                : " (absolute)"));
                lines.add("Effective Minimum Support      : "
                                + formatFiveDecimals(
                                                BigDecimal.valueOf(result.minimumSupport().effectiveThreshold())));
                lines.add("MeanW                          : " + result.globalMeanWeight().stream()
                                .mapToObj(MainWspmPreTree::formatDouble)
                                .findFirst()
                                .orElse("N/A"));
                lines.add("Result Retention Policy        : " + configuration.resultRetention());
                lines.add("Print Patterns                 : " + configuration.printPatterns());
                lines.add("Export Patterns                : " + configuration.exportPatterns());

                lines.add("");
                lines.add("========== MINING STATISTICS ==========");
                lines.add("Generated Candidates           : " + statistics.candidateCount());
                lines.add("Accepted Candidates            : " + statistics.acceptedCandidateCount());
                lines.add("Rejected Candidates            : " + statistics.rejectedCandidateCount());
                lines.add("I-Extension Candidates         : " + statistics.iCandidateCount());
                lines.add("S-Extension Candidates         : " + statistics.sCandidateCount());
                lines.add("Expanded Nodes                 : " + statistics.expandedNodeCount());
                lines.add("Number of Weighted Patterns    : " + statistics.patternCount());

                lines.add("");
                lines.add("========== MEMORY STATISTICS (MB) ==========");
                appendMemoryPhase(lines, "Dataset I/O", execution.datasetIo());
                lines.add("");
                appendMemoryPhase(lines, "Preprocessing", algorithm.preprocessing());
                lines.add("");
                lines.add("Heap Before Mining             : "
                                + formatMemory(algorithm.dfsMining().heapBeforeBytes()));
                lines.add("Heap After Mining              : "
                                + formatMemory(algorithm.heapAfterMiningBytes()));
                lines.add("Peak Heap DFS Mining           : "
                                + formatMemory(algorithm.dfsMining().observedPeakHeapBytes()));
                lines.add("");
                if (execution.outputIo().isPresent()) {
                        appendMemoryPhase(lines, "Output I/O", execution.outputIo().orElseThrow());
                } else {
                        lines.add("Heap Before Output I/O         : N/A");
                        lines.add("Heap After Output I/O          : N/A");
                        lines.add("Peak Heap Output I/O           : N/A");
                }
                lines.add("");
                lines.add("Peak Heap Algorithm            : "
                                + formatMemory(algorithm.observedPeakHeapAlgorithmBytes()));
                lines.add("Peak Heap Total Execution      : "
                                + formatMemory(execution.observedPeakHeapTotalBytes()));
                lines.add("Memory Unit                    : MB (1 MB = 1,000,000 bytes)");
                lines.add("Peak Measurement               : Observed sampled Java heap used");
                lines.add("Sampling Interval              : "
                                + execution.samplingIntervalMillis() + " ms");

                lines.add("");
                lines.add("========== TIME STATISTICS (ms) ==========");
                lines.add("Dataset I/O Time               : "
                                + formatMilliseconds(execution.datasetIo().elapsedNanos()));
                lines.add("Preprocessing Time             : "
                                + formatMilliseconds(algorithm.preprocessing().elapsedNanos()));
                lines.add("DFS Mining Time                : "
                                + formatMilliseconds(algorithm.dfsMining().elapsedNanos()));
                lines.add("Algorithm Time                 : "
                                + formatMilliseconds(algorithm.algorithmNanos()));
                lines.add("Output I/O Time                : "
                                + formatMilliseconds(execution.outputIoNanos()));
                lines.add("Total Execution Time           : "
                                + formatMilliseconds(execution.totalExecutionNanos()));
                lines.add("Total Scope                    : Excludes console printing");
                return List.copyOf(lines);
        }

        private static void appendMemoryPhase(
                        List<String> lines, String phaseName, PhaseExecutionMetrics metrics) {
                lines.add(String.format("%-32s : %s", "Heap Before " + phaseName,
                                formatMemory(metrics.heapBeforeBytes())));
                lines.add(String.format("%-32s : %s", "Heap After " + phaseName,
                                formatMemory(metrics.heapAfterBytes())));
                lines.add(String.format("%-32s : %s", "Peak Heap " + phaseName,
                                formatMemory(metrics.observedPeakHeapBytes())));
        }

        private static String formatMemory(long bytes) {
                return String.format(java.util.Locale.ROOT, "%.3f",
                                WspmExecutionMetrics.bytesToMegabytes(bytes));
        }

        private static String formatMilliseconds(long nanos) {
                return formatFiveDecimals(BigDecimal.valueOf(nanos).movePointLeft(6));
        }

        private static String formatDouble(double value) {
                return formatFiveDecimals(BigDecimal.valueOf(value));
        }

        private static String formatFiveDecimals(BigDecimal value) {
                return value.setScale(5, RoundingMode.HALF_UP).toPlainString();
        }

        static List<String> patternLines(WspmMiningResult result) {
                Objects.requireNonNull(result, "mining result must not be null");
                return result.acceptedPatterns().stream()
                                .map(pattern -> pattern.serialize() + " #SUP: " + result.support(pattern))
                                .toList();
        }

        private static void validatePaths(RunConfiguration configuration) {
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

        private static void exportPatterns(Path outputPath, List<String> lines) throws IOException {
                Path absoluteOutput = outputPath.toAbsolutePath().normalize();
                Path parent = absoluteOutput.getParent();
                if (parent != null) {
                        Files.createDirectories(parent);
                }
                String content = lines.isEmpty() ? "" : String.join("\n", lines) + "\n";
                Files.writeString(
                                absoluteOutput,
                                content,
                                StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING,
                                StandardOpenOption.WRITE);
        }

        private static void printLines(PrintStream output, List<String> lines) {
                lines.forEach(output::println);
        }

        record RunConfiguration(
                        Path datasetPath,
                        SequenceDatabaseFormat inputFormat,
                        boolean minimumSupportIsRelative,
                        double minimumSupport,
                        ResultRetentionPolicy resultRetention,
                        boolean printPatterns,
                        boolean printStatistics,
                        boolean exportPatterns,
                        Path outputPath) {

                RunConfiguration(
                                Path datasetPath,
                                SequenceDatabaseFormat inputFormat,
                                boolean minimumSupportIsRelative,
                                double minimumSupport,
                                boolean printPatterns,
                                boolean printStatistics,
                                boolean exportPatterns,
                                Path outputPath) {
                        this(
                                        datasetPath,
                                        inputFormat,
                                        minimumSupportIsRelative,
                                        minimumSupport,
                                        ResultRetentionPolicy.SUPPORT_ONLY,
                                        printPatterns,
                                        printStatistics,
                                        exportPatterns,
                                        outputPath);
                }

                RunConfiguration {
                        Objects.requireNonNull(datasetPath, "dataset path must not be null");
                        Objects.requireNonNull(inputFormat, "input format must not be null");
                        Objects.requireNonNull(resultRetention, "result retention policy must not be null");
                        Objects.requireNonNull(outputPath, "output path must not be null");
                }
        }
}
