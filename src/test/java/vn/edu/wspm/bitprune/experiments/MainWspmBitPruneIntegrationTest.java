package vn.edu.wspm.bitprune.experiments;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.wspm.bitprune.measurement.BitPruneExecutionMetrics;
import vn.edu.wspm.bitprune.mining.BitPruneMiner;
import vn.edu.wspm.bitprune.result.BitPruneMiningResult;
import vn.edu.wspm.bitprune.statistics.BitPruneStatistics;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportMode;
import vn.edu.wspm.io.SequenceDatabaseFormat;
import vn.edu.wspm.io.SequenceDatabaseParser;
import vn.edu.wspm.mining.ResultRetentionPolicy;
import vn.edu.wspm.mining.WspmMiningResult;
import vn.edu.wspm.mining.WspmPreTreeMiner;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.SequenceDatabase;

/**
 * End-to-end integration, Table 1 oracle, baseline differential, format routing,
 * and exception lifecycle test suite for {@link MainWspmBitPrune} (C15.4).
 */
class MainWspmBitPruneIntegrationTest {

    private static final Path TABLE1_PATH = Path.of("src/test/resources/datasets/wspm/table1.spmf");

    @TempDir
    Path tempDir;

    // =========================================================================
    // CATEGORY A — EXECUTABLE MAIN CONTRACT
    // =========================================================================

    @Nested
    @DisplayName("Category A — Executable Main Contract")
    class ExecutableMainTests {

        @Test
        @DisplayName("1-2. MainWspmBitPrune exposes public static void main(String[] args) throws IOException")
        void mainMethodSignature() throws NoSuchMethodException {
            Method main = MainWspmBitPrune.class.getMethod("main", String[].class);
            assertTrue(Modifier.isPublic(main.getModifiers()), "main must be public");
            assertTrue(Modifier.isStatic(main.getModifiers()), "main must be static");
            assertEquals(void.class, main.getReturnType(), "main must return void");
            assertTrue(List.of(main.getExceptionTypes()).contains(IOException.class),
                    "main must declare throws IOException");
        }

        @Test
        @DisplayName("3-9. Default main execution completes without terminal input and prints standardized console")
        void defaultMainExecution() throws IOException {
            Path defaultOutputPath = Path.of("output/wspm-bitprune-patterns.txt");
            boolean fileExistedBefore = Files.exists(defaultOutputPath);
            byte[] bytesBefore = fileExistedBefore ? Files.readAllBytes(defaultOutputPath) : null;

            PrintStream originalOut = System.out;
            var captured = new ByteArrayOutputStream();
            try {
                System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
                MainWspmBitPrune.main(new String[0]);
            } finally {
                System.setOut(originalOut);
            }

            String consoleOutput = captured.toString(StandardCharsets.UTF_8);

            // Verify console sections and key fields
            assertTrue(consoleOutput.contains("========== INPUT CONFIGURATION =========="));
            assertTrue(consoleOutput.contains("Algorithm                      : WSPM_BitPrune"));
            assertTrue(consoleOutput.contains("Dataset                        : " + TABLE1_PATH));
            assertTrue(consoleOutput.contains("Minimum Support Input          : 0.14 (relative fraction)"));
            assertTrue(consoleOutput.contains("MeanW                          : 0.18182"));
            assertTrue(consoleOutput.contains("========== MINING STATISTICS =========="));
            assertTrue(consoleOutput.contains("Number of Weighted Patterns    : 9"));
            assertTrue(consoleOutput.contains("========== MEMORY STATISTICS (MB) =========="));
            assertTrue(consoleOutput.contains("========== TIME STATISTICS (ms) =========="));

            // Default printPatterns=false -> no accepted patterns section
            assertFalse(consoleOutput.contains("=== Accepted Patterns ==="));

            // Default exportPatterns=false -> output file not created or modified
            if (!fileExistedBefore) {
                assertFalse(Files.exists(defaultOutputPath),
                        "output file must not be created when export is disabled");
            } else {
                byte[] bytesAfter = Files.readAllBytes(defaultOutputPath);
                assertArrayEquals(bytesBefore, bytesAfter,
                        "output file content must not change when export is disabled");
            }
        }
    }

    // =========================================================================
    // CATEGORY B — TABLE 1 END-TO-END ORACLE
    // =========================================================================

    @Nested
    @DisplayName("Category B — Table 1 End-to-End Oracle")
    class Table1EndToEndOracleTests {

        private BitPruneExecutionRun run;
        private Path outputPath;
        private ByteArrayOutputStream capturedOutput;

        private void executeTable1AllEnabled() throws IOException {
            outputPath = tempDir.resolve("table1_oracle_export.txt");
            var config = new BitPruneRunConfiguration(
                    TABLE1_PATH,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    0.14,
                    true,
                    true,
                    true,
                    outputPath);

            capturedOutput = new ByteArrayOutputStream();
            var printStream = new PrintStream(capturedOutput, true, StandardCharsets.UTF_8);
            run = MainWspmBitPrune.execute(config, printStream);
        }

        @Test
        @DisplayName("9.1 Exact metadata on Table 1")
        void exactMetadata() throws IOException {
            executeTable1AllEnabled();

            assertEquals(7L, run.result().statistics().sequenceCount());
            assertEquals(8, run.metadata().distinctItemCount());
            assertTrue(run.metadata().globalMeanWeight().isPresent());
            assertEquals(12.0 / 66.0, run.metadata().globalMeanWeight().getAsDouble(), 1e-9);

            var resolvedSup = run.metadata().minimumSupport();
            assertTrue(resolvedSup.relativeMinimumSupport().isPresent());
            assertEquals(0.14, resolvedSup.relativeMinimumSupport().getAsDouble(), 1e-9);
            assertEquals(0.98, resolvedSup.effectiveThreshold(), 1e-9);
            assertEquals(7, resolvedSup.numberOfSequences());
            assertEquals(MinimumSupportMode.PERCENT, resolvedSup.mode());
        }

        @Test
        @DisplayName("9.2 Exact nine-pattern support map matches independent oracle")
        void exactPatternSupportMap() throws IOException {
            executeTable1AllEnabled();

            Item item1 = new Item(1);
            Item item2 = new Item(2);
            Item item3 = new Item(3);

            Map<Pattern, Integer> expectedSupports = Map.of(
                    Pattern.singleton(item1), 7,
                    Pattern.of(Itemset.of(item1, item2)), 7,
                    Pattern.of(Itemset.of(item1, item2), Itemset.of(item3)), 6,
                    Pattern.of(Itemset.of(item1), Itemset.of(item1)), 6,
                    Pattern.of(Itemset.of(item1), Itemset.of(item2)), 7,
                    Pattern.of(Itemset.of(item1), Itemset.of(item3)), 6,
                    Pattern.singleton(item2), 7,
                    Pattern.of(Itemset.of(item2), Itemset.of(item3)), 6,
                    Pattern.singleton(item3), 6
            );

            assertEquals(expectedSupports, run.result().supports(),
                    "Table 1 support map must match the exact nine-pattern oracle");
        }

        @Test
        @DisplayName("9.3 Exact deterministic projection, file export bytes, and console lines")
        void exactDeterministicProjectionAndExport() throws IOException {
            executeTable1AllEnabled();

            List<String> expectedLines = List.of(
                    "<(1)> #SUP: 7",
                    "<(1 2)> #SUP: 7",
                    "<(1 2)(3)> #SUP: 6",
                    "<(1)(1)> #SUP: 6",
                    "<(1)(2)> #SUP: 7",
                    "<(1)(3)> #SUP: 6",
                    "<(2)> #SUP: 7",
                    "<(2)(3)> #SUP: 6",
                    "<(3)> #SUP: 6"
            );

            // 1. Projection lines
            assertEquals(expectedLines, run.projection().lines());

            // 2. Exported UTF-8 content
            assertTrue(Files.isRegularFile(outputPath));
            String fileContent = Files.readString(outputPath, StandardCharsets.UTF_8);
            assertEquals(run.projection().content(), fileContent);

            // 3. Exactly one trailing LF, no BOM, no duplicates
            assertTrue(fileContent.endsWith("\n") && !fileContent.endsWith("\n\n"),
                    "Non-empty export must end with exactly one newline character");
            assertFalse(fileContent.startsWith("\uFEFF"), "File must not contain BOM");
            assertEquals(expectedLines.size(), new HashSet<>(expectedLines).size(),
                    "Projection must not contain duplicate lines");

            // 4. Console pattern lines
            String console = capturedOutput.toString(StandardCharsets.UTF_8);
            assertTrue(console.contains("=== Accepted Patterns ==="));
            List<String> consoleLines = console.lines().toList();
            int patternHeaderIdx = consoleLines.indexOf("=== Accepted Patterns ===");
            List<String> actualConsolePatternLines = consoleLines.subList(
                    patternHeaderIdx + 1, consoleLines.size());
            assertEquals(expectedLines, actualConsolePatternLines);
        }

        @Test
        @DisplayName("9.4 Exact Table 1 candidate, pruning, and verification statistics")
        void exactStatistics() throws IOException {
            executeTable1AllEnabled();

            BitPruneStatistics stats = run.result().statistics();
            assertEquals(7L, stats.sequenceCount());
            assertEquals(3L, stats.f1Count());
            assertEquals(34L, stats.generatedExtensions());
            assertEquals(7L, stats.generatedIExtensions());
            assertEquals(27L, stats.generatedSExtensions());
            assertEquals(0L, stats.cmapRejected());
            assertEquals(0L, stats.bitmapRejected());
            assertEquals(28L, stats.exactRejected());
            assertEquals(6L, stats.acceptedExtensions());
            assertEquals(28L, stats.rejectedExtensions());
            assertEquals(9L, stats.expandedNodes());
            assertEquals(9L, stats.patternCount());
            assertEquals(34L, stats.exactVerifications());
        }

        @Test
        @DisplayName("9.5 Six-scope measurement metrics and hierarchy")
        void sixScopeMetrics() throws IOException {
            executeTable1AllEnabled();

            BitPruneExecutionMetrics metrics = run.metrics();

            assertTrue(metrics.datasetIo().elapsedNanos() >= 0L);
            assertTrue(metrics.algorithm().preprocessing().elapsedNanos() >= 0L);
            assertTrue(metrics.algorithm().dfsMining().elapsedNanos() >= 0L);

            long sumAlgo = metrics.algorithm().preprocessing().elapsedNanos()
                    + metrics.algorithm().dfsMining().elapsedNanos();
            assertTrue(metrics.algorithm().completeAlgorithmNanos() >= sumAlgo,
                    "Complete Algorithm duration must be >= Preprocessing + DFS Mining");

            assertTrue(metrics.outputIo().isPresent());
            assertTrue(metrics.outputIoNanos() >= 0L);

            long sumTotal = metrics.datasetIo().elapsedNanos()
                    + metrics.algorithm().completeAlgorithmNanos()
                    + metrics.outputIoNanos();
            assertTrue(metrics.totalExecutionNanos() >= sumTotal,
                    "Total Execution duration must be >= Dataset I/O + Complete Algorithm + Output I/O");

            // Peak coverage
            long totalPeak = metrics.observedPeakHeapTotalBytes();
            assertTrue(totalPeak >= metrics.datasetIo().observedPeakHeapBytes());
            assertTrue(totalPeak >= metrics.algorithm().observedPeakHeapAlgorithmBytes());
            assertTrue(totalPeak >= metrics.outputIo().orElseThrow().observedPeakHeapBytes());

            assertTrue(metrics.samplingIntervalMillis() > 0L);

            String console = capturedOutput.toString(StandardCharsets.UTF_8);
            assertTrue(console.contains("Total Scope                    : Excludes console formatting and printing"));
        }
    }

    // =========================================================================
    // CATEGORY C — BASELINE DIFFERENTIAL ON TABLE 1
    // =========================================================================

    @Nested
    @DisplayName("Category C — Baseline Differential on Table 1")
    class BaselineDifferentialTests {

        @Test
        @DisplayName("WSPM_PreTree baseline and WSPM_BitPrune output identical nine-pattern support map")
        void baselineVersusBitPruneDifferential() throws IOException {
            SequenceDatabase database = new SequenceDatabaseParser().parse(
                    TABLE1_PATH, SequenceDatabaseFormat.SPMF_SEQUENTIAL);

            // 1. Run baseline WSPM_PreTree
            WspmMiningResult baselineResult = new WspmPreTreeMiner().mine(
                    database,
                    MinimumSupport.percentage(14.0),
                    ResultRetentionPolicy.SUPPORT_ONLY);

            // 2. Run WSPM_BitPrune via standalone runner
            var bitPruneConfig = new BitPruneRunConfiguration(
                    TABLE1_PATH,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    0.14,
                    false,
                    false,
                    false,
                    tempDir.resolve("bitprune_diff.txt"));
            BitPruneExecutionRun bitPruneRun = MainWspmBitPrune.execute(bitPruneConfig);

            // 3. Exact support map equivalence
            Map<Pattern, Integer> baselineSupports = baselineResult.supports();
            Map<Pattern, Integer> bitPruneSupports = bitPruneRun.result().supports();

            assertEquals(9, baselineSupports.size(), "Baseline pattern count must be 9");
            assertEquals(9, bitPruneSupports.size(), "BitPrune pattern count must be 9");
            assertEquals(baselineSupports, bitPruneSupports,
                    "BitPrune complete pattern-to-support map must match baseline WSPM_PreTree exactly");

            // 4. Explicit element-by-element verification
            for (Map.Entry<Pattern, Integer> entry : baselineSupports.entrySet()) {
                Pattern pattern = entry.getKey();
                Integer expectedSupport = entry.getValue();

                assertTrue(bitPruneSupports.containsKey(pattern),
                        "BitPrune output missing pattern from baseline: " + pattern);
                assertEquals(expectedSupport, bitPruneSupports.get(pattern),
                        "Support mismatch for pattern: " + pattern);
            }
        }
    }

    // =========================================================================
    // CATEGORY D — CONFIGURED FORMAT ROUTING
    // =========================================================================

    @Nested
    @DisplayName("Category D — Configured Format Routing")
    class ConfiguredFormatRoutingTests {

        @Test
        @DisplayName("FIMI transaction database routing, parsing, metrics, and consistency")
        void fimiTransactionDatabaseRouting() throws IOException {
            Path fimiFile = tempDir.resolve("sample.fimi");
            String fimiData = "1 2 3\n1 2\n2 3 4\n1 3\n";
            Files.writeString(fimiFile, fimiData, StandardCharsets.UTF_8);

            Path outputPath = tempDir.resolve("fimi_exported.txt");
            var config = new BitPruneRunConfiguration(
                    fimiFile,
                    SequenceDatabaseFormat.FIMI_TRANSACTION,
                    true,
                    0.05, // 5% of 4 sequences -> threshold 0.20
                    true,
                    true,
                    true,
                    outputPath);

            var captured = new ByteArrayOutputStream();
            var printStream = new PrintStream(captured, true, StandardCharsets.UTF_8);
            BitPruneExecutionRun run = MainWspmBitPrune.execute(config, printStream);

            assertNotNull(run);
            assertEquals(4L, run.result().statistics().sequenceCount());
            assertEquals(4, run.metadata().distinctItemCount());
            assertTrue(run.result().statistics().patternCount() > 0);

            // Six-scope metrics valid
            assertTrue(run.metrics().datasetIo().elapsedNanos() >= 0L);
            assertTrue(run.metrics().algorithm().completeAlgorithmNanos() >= 0L);
            assertTrue(run.metrics().outputIo().isPresent());
            assertTrue(run.metrics().totalExecutionNanos() >= 0L);

            // Exported file matches projection content
            assertTrue(Files.isRegularFile(outputPath));
            assertEquals(run.projection().content(), Files.readString(outputPath, StandardCharsets.UTF_8));

            // Consistency check against direct miner on same parser database
            SequenceDatabase parsedDb = new SequenceDatabaseParser().parse(
                    fimiFile, SequenceDatabaseFormat.FIMI_TRANSACTION);
            BitPruneMiningResult directResult = new BitPruneMiner().mine(
                    parsedDb, MinimumSupport.percentage(5.0));
            assertEquals(directResult.supports(), run.result().supports(),
                    "Runner result on FIMI must match direct BitPruneMiner consistency oracle");
        }
    }

    // =========================================================================
    // CATEGORY E — EXCEPTION LIFECYCLE MATRIX
    // =========================================================================

    @Nested
    @DisplayName("Category E — Exception Lifecycle Matrix")
    class ExceptionLifecycleMatrixTests {

        private Set<Long> getAliveSamplerThreadIds() {
            return Thread.getAllStackTraces().keySet().stream()
                    .filter(t -> t.getName().startsWith("wspm-heap-sampler-") && t.isAlive())
                    .map(Thread::threadId)
                    .collect(Collectors.toSet());
        }

        private void assertNoNewSamplerThreads(Set<Long> beforeIds) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            Set<Long> leaked = Set.of();
            while (System.nanoTime() < deadline) {
                Set<Long> current = getAliveSamplerThreadIds();
                current.removeAll(beforeIds);
                if (current.isEmpty()) {
                    return;
                }
                leaked = current;
                Thread.yield();
            }
            assertTrue(leaked.isEmpty(), "Leaked sampler thread IDs: " + leaked);
        }

        @Test
        @DisplayName("12.1 Malformed parser input throws and closes sampler thread")
        void malformedParserInputFailsAndClosesSampler() throws IOException {
            Path malformedFile = tempDir.resolve("malformed.spmf");
            Files.writeString(malformedFile, "1 not_an_int -1 -2\n", StandardCharsets.UTF_8);

            var config = new BitPruneRunConfiguration(
                    malformedFile,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    0.14,
                    false,
                    false,
                    false,
                    tempDir.resolve("unused_out.txt"));

            Set<Long> beforeThreads = getAliveSamplerThreadIds();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> MainWspmBitPrune.execute(config));
            assertNotNull(ex.getMessage());

            assertNoNewSamplerThreads(beforeThreads);
        }

        @Test
        @DisplayName("12.2 R6 zero threshold throws IllegalArgumentException, prevents export, and closes sampler")
        void r6ZeroThresholdFailsAndClosesSampler() {
            var config = new BitPruneRunConfiguration(
                    TABLE1_PATH,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    false,
                    0.0,
                    false,
                    false,
                    true,
                    tempDir.resolve("r6_zero_out.txt"));

            Set<Long> beforeThreads = getAliveSamplerThreadIds();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> MainWspmBitPrune.execute(config));
            assertNotNull(ex.getMessage());

            assertFalse(Files.exists(tempDir.resolve("r6_zero_out.txt")),
                    "Output file must not be created on R6 zero-threshold failure");

            assertNoNewSamplerThreads(beforeThreads);
        }

        @Test
        @DisplayName("12.3 Output writer failure propagates IOException, leaves dataset intact, and closes sampler")
        void writerFailurePropagatesAndClosesSampler() throws IOException {
            Path parentAsRegularFile = tempDir.resolve("regular_parent_file.txt");
            Files.writeString(parentAsRegularFile, "marker", StandardCharsets.UTF_8);

            // In POSIX and Windows, creating a path under a regular file fails
            Path impossibleOutput = parentAsRegularFile.resolve("nested").resolve("out.txt");

            byte[] datasetBytesBefore = Files.readAllBytes(TABLE1_PATH);

            var config = new BitPruneRunConfiguration(
                    TABLE1_PATH,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    0.14,
                    false,
                    false,
                    true,
                    impossibleOutput);

            Set<Long> beforeThreads = getAliveSamplerThreadIds();

            IOException ex = assertThrows(IOException.class,
                    () -> MainWspmBitPrune.execute(config));
            assertNotNull(ex.getMessage());

            assertNoNewSamplerThreads(beforeThreads);

            byte[] datasetBytesAfter = Files.readAllBytes(TABLE1_PATH);
            assertArrayEquals(datasetBytesBefore, datasetBytesAfter,
                    "Dataset must remain bit-identical across writer failure");
        }
    }

    // =========================================================================
    // CATEGORY F — NONINTERACTIVE AND STATIC CONTRACT
    // =========================================================================

    @Nested
    @DisplayName("Category F — Noninteractive and Static Contract")
    class NoninteractiveAndStaticContractTests {

        @Test
        @DisplayName("Source-configured constants are private static final and hold exact expected defaults")
        void sourceConfiguredConstantsContract() throws ReflectiveOperationException {
            assertConstant("DATASET_PATH", Path.class, TABLE1_PATH);
            assertConstant("INPUT_FORMAT", SequenceDatabaseFormat.class, SequenceDatabaseFormat.SPMF_SEQUENTIAL);
            assertConstant("MIN_SUP_IS_RELATIVE", boolean.class, true);
            assertConstant("MIN_SUP", double.class, 0.14);
            assertConstant("PRINT_PATTERNS", boolean.class, false);
            assertConstant("PRINT_STATISTICS", boolean.class, true);
            assertConstant("EXPORT_PATTERNS", boolean.class, false);
            assertConstant("OUTPUT_PATH", Path.class, Path.of("output/wspm-bitprune-patterns.txt"));
        }

        private void assertConstant(String name, Class<?> type, Object expectedValue)
                throws ReflectiveOperationException {
            Field field = MainWspmBitPrune.class.getDeclaredField(name);
            int mod = field.getModifiers();
            assertTrue(Modifier.isPrivate(mod), name + " must be private");
            assertTrue(Modifier.isStatic(mod), name + " must be static");
            assertTrue(Modifier.isFinal(mod), name + " must be final");
            assertEquals(type, field.getType(), name + " type mismatch");

            field.setAccessible(true);
            Object actualValue = field.get(null);
            assertEquals(expectedValue, actualValue, name + " default value mismatch");
        }

        @Test
        @DisplayName("No Scanner, System.in, or CLI parser present in MainWspmBitPrune")
        void noInteractiveOrCliFacilities() {
            for (Method method : MainWspmBitPrune.class.getDeclaredMethods()) {
                for (Class<?> param : method.getParameterTypes()) {
                    assertFalse(param.getName().contains("Scanner"),
                            "MainWspmBitPrune must not accept Scanner parameters");
                }
            }
            for (Field field : MainWspmBitPrune.class.getDeclaredFields()) {
                assertFalse(field.getType().getName().contains("Scanner"),
                        "MainWspmBitPrune must not declare Scanner fields");
            }
        }

        @Test
        @DisplayName("BitPruneRunConfiguration has no retention policy switch")
        void noRetentionFieldInConfiguration() {
            for (Field field : BitPruneRunConfiguration.class.getDeclaredFields()) {
                assertFalse(field.getName().toLowerCase().contains("retention"),
                        "BitPruneRunConfiguration must not contain retention policy field");
            }
        }
    }
}
