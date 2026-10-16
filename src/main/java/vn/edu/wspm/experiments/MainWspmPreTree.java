package vn.edu.wspm.experiments;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.SequenceDatabaseFormat;
import vn.edu.wspm.io.SequenceDatabaseParser;
import vn.edu.wspm.mining.WspmMiningResult;
import vn.edu.wspm.mining.WspmPreTreeMiner;

/** Standalone source-configured runner for the WSPM_PreTree baseline. */
public final class MainWspmPreTree {

    private static final Path DATASET_PATH =
            Path.of("src/test/resources/datasets/wspm/table1.spmf");
    private static final SequenceDatabaseFormat INPUT_FORMAT =
            SequenceDatabaseFormat.SPMF_SEQUENTIAL;

    // true: MIN_SUP is a relative fraction in [0, 1]; false: it is absolute.
    private static final boolean MIN_SUP_IS_RELATIVE = true;
    private static final double MIN_SUP = 0.14;

    private static final boolean PRINT_PATTERNS = true;
    private static final boolean PRINT_STATISTICS = true;
    private static final boolean EXPORT_PATTERNS = false;
    private static final Path OUTPUT_PATH =
            Path.of("output/wspm-pretree-patterns.txt");

    private MainWspmPreTree() {}

    public static void main(String[] args) throws IOException {
        var configuration = new RunConfiguration(
                DATASET_PATH,
                INPUT_FORMAT,
                MIN_SUP_IS_RELATIVE,
                MIN_SUP,
                PRINT_PATTERNS,
                PRINT_STATISTICS,
                EXPORT_PATTERNS,
                OUTPUT_PATH);
        run(configuration, System.out);
    }

    static WspmMiningResult run(
            RunConfiguration configuration, PrintStream output) throws IOException {
        Objects.requireNonNull(configuration, "run configuration must not be null");
        Objects.requireNonNull(output, "console output must not be null");
        validatePaths(configuration);

        var database = new SequenceDatabaseParser().parse(
                configuration.datasetPath(), configuration.inputFormat());
        MinimumSupport minimumSupport = toMinimumSupport(
                configuration.minimumSupport(), configuration.minimumSupportIsRelative());
        var result = new WspmPreTreeMiner().mine(database, minimumSupport);

        if (configuration.printStatistics()) {
            printLines(output, summaryLines(configuration, result));
        }
        if (configuration.printPatterns()) {
            output.println("=== Accepted Patterns ===");
            printLines(output, patternLines(result));
        }
        if (configuration.exportPatterns()) {
            exportPatterns(configuration.outputPath(), patternLines(result));
        }
        return result;
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
            RunConfiguration configuration, WspmMiningResult result) {
        Objects.requireNonNull(configuration, "run configuration must not be null");
        Objects.requireNonNull(result, "mining result must not be null");
        var statistics = result.statistics();
        var lines = new ArrayList<String>();
        lines.add("=== WSPM_PreTree Summary ===");
        lines.add("Algorithm: WSPM_PreTree");
        lines.add("Dataset path: " + configuration.datasetPath());
        lines.add("Input format: " + configuration.inputFormat());
        lines.add("Minimum support input: " + configuration.minimumSupport()
                + (configuration.minimumSupportIsRelative()
                        ? " (relative fraction)"
                        : " (absolute)"));
        lines.add("Configured minimum support: "
                + result.minimumSupport().configuredValue()
                + (configuration.minimumSupportIsRelative() ? "%" : " absolute"));
        lines.add("Effective minimum support: "
                + result.minimumSupport().effectiveThreshold());
        lines.add("Sequence count: " + statistics.sequenceCount());
        lines.add("F1 count: " + statistics.f1Count());
        lines.add("Candidate count: " + statistics.candidateCount());
        lines.add("Accepted candidate count: " + statistics.acceptedCandidateCount());
        lines.add("Rejected candidate count: " + statistics.rejectedCandidateCount());
        lines.add("I-candidate count: " + statistics.iCandidateCount());
        lines.add("S-candidate count: " + statistics.sCandidateCount());
        lines.add("Expanded node count: " + statistics.expandedNodeCount());
        lines.add("Accepted pattern count: " + statistics.patternCount());
        lines.add("Elapsed time (ns): " + statistics.elapsedNanos());
        return List.copyOf(lines);
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
            boolean printPatterns,
            boolean printStatistics,
            boolean exportPatterns,
            Path outputPath) {

        RunConfiguration {
            Objects.requireNonNull(datasetPath, "dataset path must not be null");
            Objects.requireNonNull(inputFormat, "input format must not be null");
            Objects.requireNonNull(outputPath, "output path must not be null");
        }
    }
}
