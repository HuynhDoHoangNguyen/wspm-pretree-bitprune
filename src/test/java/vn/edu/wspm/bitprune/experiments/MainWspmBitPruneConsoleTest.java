package vn.edu.wspm.bitprune.experiments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.edu.wspm.bitprune.result.BitPruneMiningResult;
import vn.edu.wspm.bitprune.statistics.BitPruneStatistics;
import vn.edu.wspm.io.SequenceDatabaseFormat;

/**
 * Normative unit, console formatting, and output flag matrix tests for
 * {@link MainWspmBitPrune} (C15.3).
 */
class MainWspmBitPruneConsoleTest {

    private static final Path TABLE1_PATH = Path.of("src/test/resources/datasets/wspm/table1.spmf");
    private static final Pattern MEMORY_PATTERN = Pattern.compile("^\\d+\\.\\d{3}$");
    private static final Pattern TIME_PATTERN = Pattern.compile("^\\d+\\.\\d{5}$");

    @TempDir
    Path tempDir;

    private BitPruneRunConfiguration createConfig(
            boolean printPatterns,
            boolean printStatistics,
            boolean exportPatterns,
            Path outputPath) {
        return new BitPruneRunConfiguration(
                TABLE1_PATH,
                SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                true,
                0.14,
                printPatterns,
                printStatistics,
                exportPatterns,
                outputPath);
    }

    private static String extractValue(List<String> lines, String fieldPrefix) {
        for (String line : lines) {
            if (line.startsWith(fieldPrefix)) {
                int colonIndex = line.indexOf(':');
                if (colonIndex >= 0) {
                    return line.substring(colonIndex + 1).trim();
                }
            }
        }
        throw new IllegalArgumentException("field not found with prefix: " + fieldPrefix);
    }

    // =========================================================================
    // A. API AND NULL VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("A. API and Null Validation Contract Tests")
    class ApiAndNullValidationTests {

        @Test
        @DisplayName("1. Overload execute(configuration, PrintStream) is public static")
        void executeOverloadIsPublicStatic() throws NoSuchMethodException {
            Method method = MainWspmBitPrune.class.getMethod(
                    "execute", BitPruneRunConfiguration.class, PrintStream.class);
            assertTrue(Modifier.isPublic(method.getModifiers()), "execute overload must be public");
            assertTrue(Modifier.isStatic(method.getModifiers()), "execute overload must be static");
            assertEquals(BitPruneExecutionRun.class, method.getReturnType(),
                    "return type must be BitPruneExecutionRun");
        }

        @Test
        @DisplayName("2. execute(null, output) fails fast before execution")
        void executeNullConfigurationFailsFast() {
            var out = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> MainWspmBitPrune.execute(null, out));
            assertTrue(ex.getMessage().contains("configuration must not be null"));
        }

        @Test
        @DisplayName("3. execute(config, null) fails fast before mining and filesystem mutation")
        void executeNullOutputFailsFastBeforeMiningAndFilesystem() {
            Path outputPath = tempDir.resolve("null_output_check.txt");
            var config = createConfig(false, false, true, outputPath);

            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> MainWspmBitPrune.execute(config, null));
            assertTrue(ex.getMessage().contains("output must not be null"));
            assertFalse(Files.exists(outputPath), "output file must not be created on null PrintStream");
        }

        @Test
        @DisplayName("4. summaryLines(null, run) rejects null configuration")
        void summaryLinesNullConfigRejected() throws IOException {
            var config = createConfig(false, false, false, tempDir.resolve("dummy.txt"));
            BitPruneExecutionRun run = MainWspmBitPrune.execute(config);

            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> MainWspmBitPrune.summaryLines(null, run));
            assertTrue(ex.getMessage().contains("configuration must not be null"));
        }

        @Test
        @DisplayName("5. summaryLines(config, null) rejects null execution run")
        void summaryLinesNullRunRejected() {
            var config = createConfig(false, false, false, tempDir.resolve("dummy.txt"));

            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> MainWspmBitPrune.summaryLines(config, null));
            assertTrue(ex.getMessage().contains("executionRun must not be null"));
        }
    }

    // =========================================================================
    // B. TABLE 1 EXACT SCIENTIFIC FIELDS
    // =========================================================================

    @Nested
    @DisplayName("B. Table 1 Exact Scientific Fields Tests")
    class Table1ScientificFieldsTests {

        @Test
        @DisplayName("6-15. Table 1 relative minsup 0.14 produces exact scientific input fields")
        void table1RelativeInputFields() throws IOException {
            Path outputPath = tempDir.resolve("t1_fields.txt");
            var config = createConfig(true, true, false, outputPath);

            var bytes = new ByteArrayOutputStream();
            var printStream = new PrintStream(bytes, true, StandardCharsets.UTF_8);
            BitPruneExecutionRun run = MainWspmBitPrune.execute(config, printStream);

            List<String> lines = MainWspmBitPrune.summaryLines(config, run);

            assertTrue(lines.contains("========== INPUT CONFIGURATION =========="));
            assertEquals("WSPM_BitPrune", extractValue(lines, "Algorithm"));
            assertEquals(TABLE1_PATH.toString(), extractValue(lines, "Dataset"));
            assertEquals("SPMF_SEQUENTIAL", extractValue(lines, "Input Format"));
            assertEquals("7", extractValue(lines, "Number of Sequences"));
            assertEquals("8", extractValue(lines, "Number of Distinct Items"));
            assertEquals("0.14 (relative fraction)", extractValue(lines, "Minimum Support Input"));
            assertEquals("PERCENT", extractValue(lines, "Exact Mode / Unit"));
            assertEquals("0.14000", extractValue(lines, "Relative Fraction"));
            assertEquals("0.98000", extractValue(lines, "Effective Minimum Support"));
            assertEquals("7", extractValue(lines, "Resolution Sequence Count"));
            assertEquals("0.18182", extractValue(lines, "MeanW"));
            assertEquals("FINAL_SCIENTIFIC_RESULT", extractValue(lines, "Result Retention Model"));
            assertEquals("true", extractValue(lines, "Print Patterns"));
            assertEquals("false", extractValue(lines, "Export Patterns"));
            assertEquals("true", extractValue(lines, "Print Statistics"));
            assertEquals(outputPath + " (export disabled)", extractValue(lines, "Output Path"));
        }

        @Test
        @DisplayName("Table 1 absolute minsup produces exact scientific fields with N/A relative fraction")
        void table1AbsoluteInputFields() throws IOException {
            Path outputPath = tempDir.resolve("t1_abs.txt");
            var config = new BitPruneRunConfiguration(
                    TABLE1_PATH,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    false,
                    2.0,
                    false,
                    true,
                    true,
                    outputPath);

            BitPruneExecutionRun run = MainWspmBitPrune.execute(config);
            List<String> lines = MainWspmBitPrune.summaryLines(config, run);

            assertEquals("2.0 (absolute)", extractValue(lines, "Minimum Support Input"));
            assertEquals("ABSOLUTE", extractValue(lines, "Exact Mode / Unit"));
            assertEquals("N/A", extractValue(lines, "Relative Fraction"));
            assertEquals("2.00000", extractValue(lines, "Effective Minimum Support"));
            assertEquals("7", extractValue(lines, "Resolution Sequence Count"));
            assertEquals("0.18182", extractValue(lines, "MeanW"));
            assertEquals("FINAL_SCIENTIFIC_RESULT", extractValue(lines, "Result Retention Model"));
            assertEquals(outputPath.toString(), extractValue(lines, "Output Path"));
        }
    }

    // =========================================================================
    // C. EXACT MINING STATISTICS
    // =========================================================================

    @Nested
    @DisplayName("C. Exact Mining Statistics Tests")
    class MiningStatisticsTests {

        @Test
        @DisplayName("16-19. Table 1 mining statistics match exact result counters")
        void table1MiningStatisticsMatchResultCounters() throws IOException {
            Path outputPath = tempDir.resolve("t1_stats.txt");
            var config = createConfig(false, true, false, outputPath);
            BitPruneExecutionRun run = MainWspmBitPrune.execute(config);

            BitPruneStatistics statistics = run.result().statistics();
            List<String> lines = MainWspmBitPrune.summaryLines(config, run);

            assertTrue(lines.contains("========== MINING STATISTICS =========="));
            assertEquals(String.valueOf(statistics.f1Count()), extractValue(lines, "F1 Pattern Count (F1)"));
            assertEquals(String.valueOf(statistics.generatedExtensions()), extractValue(lines, "Generated Extensions"));
            assertEquals(String.valueOf(statistics.generatedIExtensions()), extractValue(lines, "Generated I-Extensions"));
            assertEquals(String.valueOf(statistics.generatedSExtensions()), extractValue(lines, "Generated S-Extensions"));
            assertEquals(String.valueOf(statistics.rejectedExtensions()), extractValue(lines, "Rejected Extensions"));
            assertEquals(String.valueOf(statistics.cmapRejected()), extractValue(lines, "Pruned by CMAP (Tier 1)"));
            assertEquals(String.valueOf(statistics.bitmapRejected()), extractValue(lines, "Pruned by Bitmap Bound (Tier 2)"));
            assertEquals(String.valueOf(statistics.exactRejected()), extractValue(lines, "Rejected by Exact Verif (Tier 3)"));
            assertEquals(String.valueOf(statistics.acceptedExtensions()), extractValue(lines, "Accepted Extensions"));
            assertEquals(String.valueOf(statistics.exactVerifications()), extractValue(lines, "Exact Verifications (Tier 3)"));
            assertEquals(String.valueOf(statistics.expandedNodes()), extractValue(lines, "Expanded DFS Nodes"));
            assertEquals(String.valueOf(statistics.patternCount()), extractValue(lines, "Number of Weighted Patterns"));

            // Concrete Table 1 verified oracle assertions
            assertEquals(7L, statistics.sequenceCount());
            assertEquals(3L, statistics.f1Count());
            assertEquals(34L, statistics.generatedExtensions());
            assertEquals(7L, statistics.generatedIExtensions());
            assertEquals(27L, statistics.generatedSExtensions());
            assertEquals(28L, statistics.rejectedExtensions());
            assertEquals(0L, statistics.cmapRejected());
            assertEquals(0L, statistics.bitmapRejected());
            assertEquals(28L, statistics.exactRejected());
            assertEquals(6L, statistics.acceptedExtensions());
            assertEquals(34L, statistics.exactVerifications());
            assertEquals(9L, statistics.expandedNodes());
            assertEquals(9L, statistics.patternCount());
        }
    }

    // =========================================================================
    // D. MEMORY AND TIME FORMAT
    // =========================================================================

    @Nested
    @DisplayName("D. Memory and Time Format Tests")
    class MemoryAndTimeFormatTests {

        @Test
        @DisplayName("20-26. Export-off displays exact memory/time decimal formats, N/A output, and disclaimers")
        void exportOffMemoryAndTimeFormatting() throws IOException {
            Path outputPath = tempDir.resolve("export_off_format.txt");
            var config = createConfig(false, true, false, outputPath);
            BitPruneExecutionRun run = MainWspmBitPrune.execute(config);
            List<String> lines = MainWspmBitPrune.summaryLines(config, run);

            assertTrue(lines.contains("========== MEMORY STATISTICS (MB) =========="));
            assertTrue(MEMORY_PATTERN.matcher(extractValue(lines, "Heap Before Dataset I/O")).matches());
            assertTrue(MEMORY_PATTERN.matcher(extractValue(lines, "Heap After Dataset I/O")).matches());
            assertTrue(MEMORY_PATTERN.matcher(extractValue(lines, "Peak Heap Dataset I/O")).matches());
            assertTrue(MEMORY_PATTERN.matcher(extractValue(lines, "Heap Before Preprocessing")).matches());
            assertTrue(MEMORY_PATTERN.matcher(extractValue(lines, "Heap After Preprocessing")).matches());
            assertTrue(MEMORY_PATTERN.matcher(extractValue(lines, "Peak Heap Preprocessing")).matches());
            assertTrue(MEMORY_PATTERN.matcher(extractValue(lines, "Heap Before DFS Mining")).matches());
            assertTrue(MEMORY_PATTERN.matcher(extractValue(lines, "Heap After DFS Mining")).matches());
            assertTrue(MEMORY_PATTERN.matcher(extractValue(lines, "Peak Heap DFS Mining")).matches());

            assertEquals("N/A", extractValue(lines, "Heap Before Output I/O"));
            assertEquals("N/A", extractValue(lines, "Heap After Output I/O"));
            assertEquals("N/A", extractValue(lines, "Peak Heap Output I/O"));

            assertTrue(MEMORY_PATTERN.matcher(extractValue(lines, "Peak Heap Complete Algorithm")).matches());
            assertTrue(MEMORY_PATTERN.matcher(extractValue(lines, "Peak Heap Total Execution")).matches());
            assertEquals("MB (1 MB = 1,000,000 bytes)", extractValue(lines, "Memory Unit"));
            assertEquals("Observed sampled Java heap used", extractValue(lines, "Peak Measurement"));
            assertEquals(run.metrics().samplingIntervalMillis() + " ms", extractValue(lines, "Sampling Interval"));

            assertTrue(lines.contains("========== TIME STATISTICS (ms) =========="));
            assertTrue(TIME_PATTERN.matcher(extractValue(lines, "Dataset I/O Time")).matches());
            assertTrue(TIME_PATTERN.matcher(extractValue(lines, "Preprocessing Time")).matches());
            assertTrue(TIME_PATTERN.matcher(extractValue(lines, "DFS Mining Time")).matches());
            assertTrue(TIME_PATTERN.matcher(extractValue(lines, "Complete Algorithm Time")).matches());
            assertEquals("0.00000", extractValue(lines, "Output I/O Time"));
            assertTrue(TIME_PATTERN.matcher(extractValue(lines, "Total Execution Time")).matches());
            assertEquals("Excludes console formatting and printing", extractValue(lines, "Total Scope"));
        }

        @Test
        @DisplayName("Export-on displays finite 3-decimal memory and 5-decimal time for Output I/O")
        void exportOnMemoryAndTimeFormatting() throws IOException {
            Path outputPath = tempDir.resolve("export_on_format.txt");
            var config = createConfig(false, true, true, outputPath);
            BitPruneExecutionRun run = MainWspmBitPrune.execute(config);
            List<String> lines = MainWspmBitPrune.summaryLines(config, run);

            assertTrue(MEMORY_PATTERN.matcher(extractValue(lines, "Heap Before Output I/O")).matches());
            assertTrue(MEMORY_PATTERN.matcher(extractValue(lines, "Heap After Output I/O")).matches());
            assertTrue(MEMORY_PATTERN.matcher(extractValue(lines, "Peak Heap Output I/O")).matches());
            assertTrue(TIME_PATTERN.matcher(extractValue(lines, "Output I/O Time")).matches());
        }
    }

    // =========================================================================
    // E. PATTERN PRINTING
    // =========================================================================

    @Nested
    @DisplayName("E. Pattern Printing Contract Tests")
    class PatternPrintingTests {

        @Test
        @DisplayName("27-31. When printPatterns is true, lines match run.projection().lines() exactly")
        void patternPrintingMatchesProjectionLines() throws IOException {
            Path outputPath = tempDir.resolve("pattern_print.txt");
            var config = createConfig(true, false, false, outputPath);

            var bytes = new ByteArrayOutputStream();
            var printStream = new PrintStream(bytes, true, StandardCharsets.UTF_8);
            BitPruneExecutionRun run = MainWspmBitPrune.execute(config, printStream);

            List<String> consoleLines = bytes.toString(StandardCharsets.UTF_8).lines().toList();
            assertFalse(consoleLines.isEmpty());
            assertEquals("=== Accepted Patterns ===", consoleLines.getFirst());

            List<String> patternLines = consoleLines.subList(1, consoleLines.size());
            assertEquals(run.projection().lines(), patternLines);
            assertEquals(9, patternLines.size());

            // Distinct patterns
            assertEquals(patternLines.size(), patternLines.stream().distinct().count());

            // Carrier projection reused
            assertSame(run.projection().lines(), run.projection().lines());
        }

        @Test
        @DisplayName("When printPatterns is false, no pattern header or lines are output")
        void patternPrintingSuppressedWhenFlagIsFalse() throws IOException {
            Path outputPath = tempDir.resolve("pattern_suppressed.txt");
            var config = createConfig(false, true, false, outputPath);

            var bytes = new ByteArrayOutputStream();
            var printStream = new PrintStream(bytes, true, StandardCharsets.UTF_8);
            MainWspmBitPrune.execute(config, printStream);

            String consoleOutput = bytes.toString(StandardCharsets.UTF_8);
            assertFalse(consoleOutput.contains("=== Accepted Patterns ==="));
            assertFalse(consoleOutput.contains("#SUP:"));
        }

        @Test
        @DisplayName("Empty projection prints only pattern header without synthetic patterns")
        void emptyProjectionPrintsOnlyHeader() throws IOException {
            Path outputPath = tempDir.resolve("empty_pattern.txt");
            // Set threshold to impossible 100.0 relative minsup on Table 1 to produce 0 patterns
            var config = new BitPruneRunConfiguration(
                    TABLE1_PATH,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    1.0, // 100% minsup -> effective minsup 7.0; max weighted support is 7 * 0.18182 = 1.27274 < 7.0
                    true,
                    false,
                    false,
                    outputPath);

            var bytes = new ByteArrayOutputStream();
            var printStream = new PrintStream(bytes, true, StandardCharsets.UTF_8);
            BitPruneExecutionRun run = MainWspmBitPrune.execute(config, printStream);

            assertEquals(0, run.result().statistics().patternCount());
            assertEquals(List.of(), run.projection().lines());

            List<String> consoleLines = bytes.toString(StandardCharsets.UTF_8).lines().toList();
            assertEquals(List.of("=== Accepted Patterns ==="), consoleLines);
        }
    }

    // =========================================================================
    // F. EIGHT-COMBINATION FLAG MATRIX
    // =========================================================================

    @Nested
    @DisplayName("F. Eight-Combination Flag Matrix Tests")
    class EightCombinationMatrixTests {

        @ParameterizedTest(name = "[{index}] printPatterns={0}, printStatistics={1}, exportPatterns={2}")
        @CsvSource({
                "false, false, false",
                "false, false, true",
                "false, true,  false",
                "false, true,  true",
                "true,  false, false",
                "true,  false, true",
                "true,  true,  false",
                "true,  true,  true"
        })
        @DisplayName("32-38. Full 8-combination output flag matrix evaluation")
        void testFlagCombination(boolean printPatterns, boolean printStatistics, boolean exportPatterns)
                throws IOException {
            String testSubDir = String.format("matrix_%b_%b_%b", printPatterns, printStatistics, exportPatterns);
            Path outputPath = tempDir.resolve(testSubDir).resolve("output.txt");

            var config = createConfig(printPatterns, printStatistics, exportPatterns, outputPath);

            var bytes = new ByteArrayOutputStream();
            var printStream = new PrintStream(bytes, true, StandardCharsets.UTF_8);
            BitPruneExecutionRun run = MainWspmBitPrune.execute(config, printStream);

            assertNotNull(run);
            BitPruneMiningResult result = run.result();
            assertEquals(9, result.statistics().patternCount());

            // Independent reference run with all output flags false and export false
            Path unusedReferenceOutput = tempDir.resolve(testSubDir).resolve("unused_reference.txt");
            var referenceConfig = new BitPruneRunConfiguration(
                    TABLE1_PATH,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    0.14,
                    false,
                    false,
                    false,
                    unusedReferenceOutput);
            BitPruneExecutionRun referenceRun = MainWspmBitPrune.execute(referenceConfig);
            assertNotNull(referenceRun);
            assertFalse(Files.exists(unusedReferenceOutput),
                    "Reference run must not create output file or directory");

            assertEquals(
                    referenceRun.result().acceptedPatterns(),
                    run.result().acceptedPatterns(),
                    "Result acceptedPatterns must match reference run exactly");

            assertEquals(
                    referenceRun.result().supports(),
                    run.result().supports(),
                    "Result pattern-to-support mapping must remain invariant across flag combinations");

            String consoleOutput = bytes.toString(StandardCharsets.UTF_8);

            // 1. Console behavior
            if (!printPatterns && !printStatistics) {
                assertEquals(0, bytes.size(), "console output must be empty when both print flags are false");
            }

            if (printStatistics) {
                assertTrue(consoleOutput.contains("========== INPUT CONFIGURATION =========="));
                assertTrue(consoleOutput.contains("========== MINING STATISTICS =========="));
                assertTrue(consoleOutput.contains("========== MEMORY STATISTICS (MB) =========="));
                assertTrue(consoleOutput.contains("========== TIME STATISTICS (ms) =========="));
            } else {
                assertFalse(consoleOutput.contains("========== INPUT CONFIGURATION =========="));
                assertFalse(consoleOutput.contains("========== MINING STATISTICS =========="));
                assertFalse(consoleOutput.contains("========== MEMORY STATISTICS (MB) =========="));
                assertFalse(consoleOutput.contains("========== TIME STATISTICS (ms) =========="));
            }

            if (printPatterns) {
                assertTrue(consoleOutput.contains("=== Accepted Patterns ==="));
                for (String line : run.projection().lines()) {
                    assertTrue(consoleOutput.contains(line));
                }
            } else {
                assertFalse(consoleOutput.contains("=== Accepted Patterns ==="));
            }

            // 2. Export file behavior
            if (exportPatterns) {
                assertTrue(Files.isRegularFile(outputPath), "output file must exist when exportPatterns is true");
                String fileContent = Files.readString(outputPath, StandardCharsets.UTF_8);
                assertEquals(run.projection().content(), fileContent,
                        "exported content must match projection content");
                assertTrue(run.metrics().outputIo().isPresent(),
                        "outputIo metrics must be present when exportPatterns is true");
            } else {
                assertFalse(Files.exists(outputPath), "output file must not exist when exportPatterns is false");
                assertFalse(Files.exists(outputPath.getParent()), "output parent dir must not exist when exportPatterns is false");
                assertTrue(run.metrics().outputIo().isEmpty(),
                        "outputIo metrics must be empty when exportPatterns is false");
            }
        }
    }

    // =========================================================================
    // G. CONSOLE EXCLUSION EVIDENCE
    // =========================================================================

    @Nested
    @DisplayName("G. Console Exclusion Evidence Tests")
    class ConsoleExclusionEvidenceTests {

        @Test
        @DisplayName("39. execute(configuration) does not write to standard out")
        void executeWithoutPrintStreamDoesNotWriteToConsole() throws IOException {
            PrintStream originalOut = System.out;
            var captured = new ByteArrayOutputStream();
            try {
                System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
                Path outputPath = tempDir.resolve("silent.txt");
                var config = createConfig(true, true, true, outputPath);
                BitPruneExecutionRun run = MainWspmBitPrune.execute(config);

                assertNotNull(run);
                assertEquals(0, captured.size(), "execute(configuration) must write zero bytes to System.out");
            } finally {
                System.setOut(originalOut);
            }
        }

        @Test
        @DisplayName("40-41. Console writing occurs strictly after core execution finishes, file is written, and sampler is closed")
        void consoleWritingOccursAfterCoreExecutionCompletes() throws IOException {
            Path outputPath = tempDir.resolve("probe_export.txt");
            var config = createConfig(true, true, true, outputPath);

            var probeStream = new FirstWriteProbeStream(outputPath, true);
            var printStream = new PrintStream(probeStream, true, StandardCharsets.UTF_8);

            BitPruneExecutionRun run = MainWspmBitPrune.execute(config, printStream);
            assertNotNull(run);

            assertTrue(probeStream.wasFirstWriteObserved(),
                    "Probe stream should have observed write operations");
            assertTrue(probeStream.fileExistedAtFirstWrite(),
                    "Export file must have already been written to disk before the first console write occurred");
            assertTrue(probeStream.noSamplerThreadsAtFirstWrite(),
                    "Sampler threads must have already closed and joined before the first console write occurred");

            // Verify file content on disk matches run projection
            assertEquals(run.projection().content(), Files.readString(outputPath, StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("42. After execute(config, output) returns, no sampler threads remain alive")
        void noSamplerThreadsAliveAfterExecuteReturns() throws IOException {
            Path outputPath = tempDir.resolve("sampler_cleanup.txt");
            var config = createConfig(true, true, false, outputPath);

            var bytes = new ByteArrayOutputStream();
            var printStream = new PrintStream(bytes, true, StandardCharsets.UTF_8);

            BitPruneExecutionRun run = MainWspmBitPrune.execute(config, printStream);
            assertNotNull(run);

            boolean activeSamplerAlive = Thread.getAllStackTraces().keySet().stream()
                    .anyMatch(t -> t.getName().startsWith("wspm-heap-sampler-") && t.isAlive());
            assertFalse(activeSamplerAlive, "No wspm-heap-sampler thread should remain alive after execution returns");
        }
    }

    // =========================================================================
    // PROBE STREAM HELPER
    // =========================================================================

    private static class FirstWriteProbeStream extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final Path expectedExportPath;
        private final boolean exportPatterns;
        private final AtomicBoolean firstWriteSeen = new AtomicBoolean(false);
        private final AtomicBoolean fileExistedAtFirstWrite = new AtomicBoolean(false);
        private final AtomicBoolean noSamplerThreadsAtFirstWrite = new AtomicBoolean(false);

        FirstWriteProbeStream(Path expectedExportPath, boolean exportPatterns) {
            this.expectedExportPath = expectedExportPath;
            this.exportPatterns = exportPatterns;
        }

        @Override
        public synchronized void write(int b) {
            onFirstWrite();
            buffer.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            onFirstWrite();
            buffer.write(b, off, len);
        }

        private void onFirstWrite() {
            if (firstWriteSeen.compareAndSet(false, true)) {
                if (exportPatterns && expectedExportPath != null) {
                    fileExistedAtFirstWrite.set(Files.isRegularFile(expectedExportPath));
                }
                boolean noActiveSamplers = Thread.getAllStackTraces().keySet().stream()
                        .noneMatch(t -> t.getName().startsWith("wspm-heap-sampler-") && t.isAlive());
                noSamplerThreadsAtFirstWrite.set(noActiveSamplers);
            }
        }

        boolean wasFirstWriteObserved() {
            return firstWriteSeen.get();
        }

        boolean fileExistedAtFirstWrite() {
            return fileExistedAtFirstWrite.get();
        }

        boolean noSamplerThreadsAtFirstWrite() {
            return noSamplerThreadsAtFirstWrite.get();
        }
    }
}
