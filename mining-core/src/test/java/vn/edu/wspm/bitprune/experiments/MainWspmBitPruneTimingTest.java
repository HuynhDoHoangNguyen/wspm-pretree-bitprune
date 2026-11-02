package vn.edu.wspm.bitprune.experiments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.wspm.bitprune.measurement.BitPruneAlgorithmMetrics;
import vn.edu.wspm.bitprune.measurement.BitPruneExecutionMetrics;
import vn.edu.wspm.bitprune.mining.BitPruneRunMetadata;
import vn.edu.wspm.bitprune.result.BitPruneMiningResult;
import vn.edu.wspm.bitprune.result.BitPruneOutputEntry;
import vn.edu.wspm.bitprune.result.BitPruneOutputProjection;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.SequenceDatabaseFormat;
import vn.edu.wspm.measurement.PhaseExecutionMetrics;

/**
 * Normative unit, timing boundary, filesystem, and lifecycle tests for
 * {@link MainWspmBitPrune} and {@link BitPruneExecutionRun} (C15.2).
 */
class MainWspmBitPruneTimingTest {

    private static final Path TABLE1_PATH = Path.of("src/test/resources/datasets/wspm/table1.spmf");

    @TempDir
    Path tempDir;

    // =========================================================================
    // A. API / CARRIER CONTRACT
    // =========================================================================

    @Nested
    @DisplayName("A. Carrier and API Contract Tests")
    class CarrierContractTests {

        @Test
        @DisplayName("1. BitPruneExecutionRun is a public record with exactly four non-null components")
        void isPublicRecordWithFourComponents() {
            assertTrue(BitPruneExecutionRun.class.isRecord(),
                    "BitPruneExecutionRun must be a Java record");
            assertTrue(Modifier.isPublic(BitPruneExecutionRun.class.getModifiers()),
                    "BitPruneExecutionRun must be a public class");

            RecordComponent[] components = BitPruneExecutionRun.class.getRecordComponents();
            assertNotNull(components);
            assertEquals(4, components.length, "BitPruneExecutionRun must have exactly 4 components");

            Map<String, Class<?>> expectedComponents = Map.of(
                    "result", BitPruneMiningResult.class,
                    "metrics", BitPruneExecutionMetrics.class,
                    "metadata", BitPruneRunMetadata.class,
                    "projection", BitPruneOutputProjection.class);

            for (RecordComponent component : components) {
                String name = component.getName();
                assertTrue(expectedComponents.containsKey(name),
                        "Unexpected component: " + name);
                assertEquals(expectedComponents.get(name), component.getType(),
                        "Type mismatch for component: " + name);
            }
        }

        @Test
        @DisplayName("2. BitPruneExecutionRun rejects null for any component")
        void rejectsNullComponents() throws IOException {
            BitPruneRunConfiguration config = new BitPruneRunConfiguration(
                    TABLE1_PATH,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    0.14,
                    false,
                    false,
                    false,
                    tempDir.resolve("dummy.txt"));

            BitPruneExecutionRun run = MainWspmBitPrune.execute(config);

            assertThrows(NullPointerException.class, () -> new BitPruneExecutionRun(
                    null, run.metrics(), run.metadata(), run.projection()));
            assertThrows(NullPointerException.class, () -> new BitPruneExecutionRun(
                    run.result(), null, run.metadata(), run.projection()));
            assertThrows(NullPointerException.class, () -> new BitPruneExecutionRun(
                    run.result(), run.metrics(), null, run.projection()));
            assertThrows(NullPointerException.class, () -> new BitPruneExecutionRun(
                    run.result(), run.metrics(), run.metadata(), null));
        }

        @Test
        @DisplayName("3. BitPruneExecutionRun does not retain sampler, database, parser, or mutable state")
        void doesNotRetainTransientOrMutableState() {
            Field[] fields = BitPruneExecutionRun.class.getDeclaredFields();
            Set<String> componentNames = Set.of("result", "metrics", "metadata", "projection");
            for (Field field : fields) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    assertTrue(componentNames.contains(field.getName()),
                            "Unexpected instance field in record: " + field.getName());
                }
            }

            // Verify constructor of MainWspmBitPrune is non-public and final
            assertTrue(Modifier.isFinal(MainWspmBitPrune.class.getModifiers()),
                    "MainWspmBitPrune must be final");
            Constructor<?>[] constructors = MainWspmBitPrune.class.getDeclaredConstructors();
            assertEquals(1, constructors.length, "MainWspmBitPrune must have exactly 1 constructor");
            assertTrue(Modifier.isPrivate(constructors[0].getModifiers()),
                    "MainWspmBitPrune constructor must be private");
        }
    }

    // =========================================================================
    // B. PATH VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("B. Path Validation Contract Tests")
    class PathValidationTests {

        @Test
        @DisplayName("4. execute(null) fails fast with NullPointerException")
        void executeNullFailsFast() {
            assertThrows(NullPointerException.class, () -> MainWspmBitPrune.execute(null));
        }

        @Test
        @DisplayName("5. Non-existent dataset path is rejected before sampler initialization")
        void nonExistentDatasetPathRejected() {
            Path nonExistent = tempDir.resolve("missing_dataset.spmf");
            BitPruneRunConfiguration config = new BitPruneRunConfiguration(
                    nonExistent,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    0.14,
                    false,
                    false,
                    false,
                    tempDir.resolve("output.txt"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> MainWspmBitPrune.execute(config));
            assertTrue(ex.getMessage().contains("dataset file does not exist or is not a regular file"),
                    "Expected regular file message; got: " + ex.getMessage());
        }

        @Test
        @DisplayName("6. Directory path is rejected as dataset path")
        void directoryPathRejected() throws IOException {
            Path dir = tempDir.resolve("some_dir");
            Files.createDirectories(dir);

            BitPruneRunConfiguration config = new BitPruneRunConfiguration(
                    dir,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    0.14,
                    false,
                    false,
                    false,
                    tempDir.resolve("output.txt"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> MainWspmBitPrune.execute(config));
            assertTrue(ex.getMessage().contains("dataset file does not exist or is not a regular file"),
                    "Expected regular file message; got: " + ex.getMessage());
        }

        @Test
        @DisplayName("7. Normalized input == normalized output is rejected when export is enabled")
        void exportPathCollidingWithDatasetRejected() throws IOException {
            Path dataset = tempDir.resolve("dataset.spmf");
            Files.writeString(dataset, "1 -1 2 -1 -2\n");

            // Direct match
            BitPruneRunConfiguration configDirect = new BitPruneRunConfiguration(
                    dataset,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    0.14,
                    false,
                    false,
                    true,
                    dataset);
            IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                    () -> MainWspmBitPrune.execute(configDirect));
            assertTrue(ex1.getMessage().contains("must not overwrite the input dataset"),
                    "Expected overwrite message; got: " + ex1.getMessage());

            // Non-canonical match that normalizes to identical absolute path
            Path nonCanonical = tempDir.resolve(".").resolve(dataset.getFileName());
            BitPruneRunConfiguration configNonCanonical = new BitPruneRunConfiguration(
                    dataset,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    0.14,
                    false,
                    false,
                    true,
                    nonCanonical);
            IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                    () -> MainWspmBitPrune.execute(configNonCanonical));
            assertTrue(ex2.getMessage().contains("must not overwrite the input dataset"),
                    "Expected overwrite message; got: " + ex2.getMessage());
        }

        @Test
        @DisplayName("8. Path validation failure does not create output directory or file")
        void validationFailureDoesNotCreateFilesystemArtifacts() {
            Path nonExistent = tempDir.resolve("missing_dataset.spmf");
            Path outputDir = tempDir.resolve("never_created_dir");
            Path outputFile = outputDir.resolve("out.txt");

            BitPruneRunConfiguration config = new BitPruneRunConfiguration(
                    nonExistent,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    0.14,
                    false,
                    false,
                    true,
                    outputFile);

            assertThrows(IllegalArgumentException.class, () -> MainWspmBitPrune.execute(config));
            assertFalse(Files.exists(outputDir), "Output directory must not be created on validation failure");
            assertFalse(Files.exists(outputFile), "Output file must not be created on validation failure");
        }

        @Test
        @DisplayName("9. When export is disabled, output path is neither validated for overwrite nor created")
        void exportDisabledDoesNotAccessOrCheckOutput() throws IOException {
            // Even if outputPath points to datasetPath, when export is false, it is not rejected
            BitPruneRunConfiguration configSamePath = new BitPruneRunConfiguration(
                    TABLE1_PATH,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    0.14,
                    false,
                    false,
                    false,
                    TABLE1_PATH);

            BitPruneExecutionRun run = MainWspmBitPrune.execute(configSamePath);
            assertNotNull(run);

            // Output in non-existent directory must not be created
            Path uncreatedDir = tempDir.resolve("uncreated_dir_test");
            Path uncreatedOut = uncreatedDir.resolve("out.txt");
            BitPruneRunConfiguration configUncreated = new BitPruneRunConfiguration(
                    TABLE1_PATH,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    0.14,
                    false,
                    false,
                    false,
                    uncreatedOut);

            BitPruneExecutionRun run2 = MainWspmBitPrune.execute(configUncreated);
            assertNotNull(run2);
            assertFalse(Files.exists(uncreatedDir), "Output directory must not be created when export is disabled");
            assertFalse(Files.exists(uncreatedOut), "Output file must not be created when export is disabled");
        }
    }

    // =========================================================================
    // C. SIX-SCOPE SUCCESSFUL RUN
    // =========================================================================

    @Nested
    @DisplayName("C. Six-Scope Successful Run Tests")
    class SixScopeTests {

        @Test
        @DisplayName("10-19. Full six-scope execution on Table 1 with positive threshold")
        void sixScopeExecutionOnTable1() throws IOException {
            Path outputPath = tempDir.resolve("scope_test/out.txt");
            BitPruneRunConfiguration config = new BitPruneRunConfiguration(
                    TABLE1_PATH,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    0.14,
                    false,
                    false,
                    true,
                    outputPath);

            BitPruneExecutionRun run = MainWspmBitPrune.execute(config);
            assertNotNull(run);

            BitPruneExecutionMetrics execution = run.metrics();
            BitPruneAlgorithmMetrics algorithm = execution.algorithm();

            // 11. Scope 1 — Dataset I/O metrics non-negative
            PhaseExecutionMetrics datasetIo = execution.datasetIo();
            assertNotNull(datasetIo);
            assertTrue(datasetIo.elapsedNanos() >= 0L, "datasetIo elapsedNanos must be >= 0");
            assertTrue(datasetIo.heapBeforeBytes() >= 0L, "datasetIo heapBefore must be >= 0");
            assertTrue(datasetIo.heapAfterBytes() >= 0L, "datasetIo heapAfter must be >= 0");
            assertTrue(datasetIo.observedPeakHeapBytes() >= 0L, "datasetIo observedPeak must be >= 0");

            // 12. Scope 2 — Preprocessing metrics non-negative
            PhaseExecutionMetrics preprocessing = algorithm.preprocessing();
            assertNotNull(preprocessing);
            assertTrue(preprocessing.elapsedNanos() >= 0L, "preprocessing elapsedNanos must be >= 0");
            assertTrue(preprocessing.heapBeforeBytes() >= 0L, "preprocessing heapBefore must be >= 0");
            assertTrue(preprocessing.heapAfterBytes() >= 0L, "preprocessing heapAfter must be >= 0");
            assertTrue(preprocessing.observedPeakHeapBytes() >= 0L, "preprocessing observedPeak must be >= 0");

            // 13. Scope 3 — DFS Mining metrics non-negative
            PhaseExecutionMetrics dfs = algorithm.dfsMining();
            assertNotNull(dfs);
            assertTrue(dfs.elapsedNanos() >= 0L, "dfsMining elapsedNanos must be >= 0");
            assertTrue(dfs.heapBeforeBytes() >= 0L, "dfsMining heapBefore must be >= 0");
            assertTrue(dfs.heapAfterBytes() >= 0L, "dfsMining heapAfter must be >= 0");
            assertTrue(dfs.observedPeakHeapBytes() >= 0L, "dfsMining observedPeak must be >= 0");

            // 14. Scope 4 — Complete Algorithm metrics non-negative and covers preprocessing + DFS
            assertTrue(algorithm.completeAlgorithmNanos() >= 0L,
                    "completeAlgorithmNanos must be >= 0");
            assertTrue(algorithm.completeAlgorithmNanos() >= preprocessing.elapsedNanos() + dfs.elapsedNanos(),
                    "completeAlgorithmNanos must cover preprocessing and dfsMining");

            // 15. Scope 6 — Total Execution non-negative and covers Dataset I/O + Algorithm + Output I/O
            assertTrue(execution.totalExecutionNanos() >= 0L,
                    "totalExecutionNanos must be >= 0");
            long requiredTotalNanos = datasetIo.elapsedNanos()
                    + algorithm.completeAlgorithmNanos()
                    + execution.outputIoNanos();
            assertTrue(execution.totalExecutionNanos() >= requiredTotalNanos,
                    "totalExecutionNanos must cover dataset I/O, algorithm, and output I/O");

            // 16. Total heap peak covers every measured child scope
            long maxChildPeak = Math.max(datasetIo.observedPeakHeapBytes(),
                    Math.max(algorithm.observedPeakHeapAlgorithmBytes(),
                            execution.outputIo().map(PhaseExecutionMetrics::observedPeakHeapBytes).orElse(0L)));
            assertTrue(execution.observedPeakHeapTotalBytes() >= maxChildPeak,
                    "observedPeakHeapTotalBytes must cover all measured child scopes");

            // 17. Sampling interval matches sampler default (5ms) and is positive
            assertEquals(5L, execution.samplingIntervalMillis());
            assertTrue(execution.samplingIntervalMillis() > 0L);

            // 18. Metadata sequence count matches result statistics
            assertEquals(7, run.result().statistics().sequenceCount());
            assertEquals(run.result().statistics().sequenceCount(),
                    run.metadata().minimumSupport().numberOfSequences());
            assertEquals(7, run.metadata().minimumSupport().numberOfSequences());
            assertTrue(run.metadata().globalMeanWeight().isPresent());

            // 19. Projection accurately matches accepted patterns / supports of result
            BitPruneOutputProjection projection = run.projection();
            assertNotNull(projection);
            assertEquals(run.result().acceptedPatterns().size(), projection.entries().size());
            for (BitPruneOutputEntry entry : projection.entries()) {
                assertTrue(run.result().acceptedPatterns().contains(entry.pattern()),
                        "Pattern in projection must belong to accepted patterns: " + entry.pattern().serialize());
                assertEquals(run.result().support(entry.pattern()), entry.support(),
                        "Support in projection entry must match result support");
            }
        }
    }

    // =========================================================================
    // D. EXPORT DISABLED
    // =========================================================================

    @Nested
    @DisplayName("D. Export Disabled Tests")
    class ExportDisabledTests {

        @Test
        @DisplayName("20-23. When export is disabled, outputIo is empty, 0ns, and no file is created")
        void exportDisabledBehavior() throws IOException {
            Path outputPath = tempDir.resolve("export_disabled_dir/out.txt");
            BitPruneRunConfiguration config = new BitPruneRunConfiguration(
                    TABLE1_PATH,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    0.14,
                    false,
                    false,
                    false,
                    outputPath);

            BitPruneExecutionRun run = MainWspmBitPrune.execute(config);

            // 20. outputIo is empty
            assertTrue(run.metrics().outputIo().isEmpty(), "outputIo must be empty when export is disabled");

            // 21. outputIoNanos is 0
            assertEquals(0L, run.metrics().outputIoNanos(), "outputIoNanos must be 0 when export is disabled");

            // 22. No directory or file created
            assertFalse(Files.exists(outputPath.getParent()), "Parent dir must not exist");
            assertFalse(Files.exists(outputPath), "Output file must not exist");

            // 23. Projection is still generated and valid
            assertNotNull(run.projection());
            assertEquals(run.result().acceptedPatterns().size(), run.projection().entries().size());
            assertFalse(run.projection().content().isEmpty(), "Table 1 at minsup 0.14 has patterns, content not empty");
        }
    }

    // =========================================================================
    // E. EXPORT ENABLED
    // =========================================================================

    @Nested
    @DisplayName("E. Export Enabled Tests")
    class ExportEnabledTests {

        @Test
        @DisplayName("24-29. When export is enabled, outputIo is measured, content matches projection, truncates existing")
        void exportEnabledBehavior() throws IOException, NoSuchAlgorithmException {
            Path outputPath = tempDir.resolve("export_enabled_dir/out.txt");

            // Pre-record dataset SHA-256 to ensure dataset is never modified
            byte[] datasetBytesBefore = Files.readAllBytes(TABLE1_PATH);
            String datasetShaBefore = sha256Hex(datasetBytesBefore);

            // Case A: Fresh file write
            BitPruneRunConfiguration configFresh = new BitPruneRunConfiguration(
                    TABLE1_PATH,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    0.14,
                    false,
                    false,
                    true,
                    outputPath);

            BitPruneExecutionRun runFresh = MainWspmBitPrune.execute(configFresh);

            // 24. outputIo is present
            assertTrue(runFresh.metrics().outputIo().isPresent(), "outputIo must be present when export is enabled");

            // 25. Output metrics non-negative
            PhaseExecutionMetrics outMetrics = runFresh.metrics().outputIo().get();
            assertTrue(outMetrics.elapsedNanos() >= 0L);
            assertTrue(outMetrics.heapBeforeBytes() >= 0L);
            assertTrue(outMetrics.heapAfterBytes() >= 0L);
            assertTrue(outMetrics.observedPeakHeapBytes() >= 0L);

            // 26. Output file is created
            assertTrue(Files.isRegularFile(outputPath), "Output file must be a regular file");

            // 27. Byte/content matches projection content exactly
            String writtenContent = Files.readString(outputPath, StandardCharsets.UTF_8);
            assertEquals(runFresh.projection().content(), writtenContent,
                    "Written file content must equal projection content exactly");

            // 28. Existing output truncated and replaced (C14 writer contract)
            Files.writeString(outputPath, "EXISTING CORRUPT DATA THAT MUST BE OVERWRITTEN");
            BitPruneExecutionRun runOverwrite = MainWspmBitPrune.execute(configFresh);
            String overwrittenContent = Files.readString(outputPath, StandardCharsets.UTF_8);
            assertEquals(runOverwrite.projection().content(), overwrittenContent,
                    "Overwritten file content must equal new projection content exactly");
            assertFalse(overwrittenContent.contains("EXISTING CORRUPT DATA"),
                    "File must be truncated, not appended");

            // 29. Dataset was not modified or overwritten
            byte[] datasetBytesAfter = Files.readAllBytes(TABLE1_PATH);
            assertEquals(datasetShaBefore, sha256Hex(datasetBytesAfter),
                    "Dataset file must remain strictly byte-identical");
        }
    }

    // =========================================================================
    // F. FLAG ISOLATION
    // =========================================================================

    @Nested
    @DisplayName("F. Flag Isolation Tests")
    class FlagIsolationTests {

        @Test
        @DisplayName("30-31. printPatterns and printStatistics do not affect mining results or projection")
        void flagIsolation() throws IOException {
            Path outputA = tempDir.resolve("flags/outA.txt");
            Path outputB = tempDir.resolve("flags/outB.txt");

            BitPruneRunConfiguration configAllFalse = new BitPruneRunConfiguration(
                    TABLE1_PATH,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    0.14,
                    false, // printPatterns = false
                    false, // printStatistics = false
                    true,
                    outputA);

            BitPruneRunConfiguration configAllTrue = new BitPruneRunConfiguration(
                    TABLE1_PATH,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    0.14,
                    true,  // printPatterns = true
                    true,  // printStatistics = true
                    true,
                    outputB);

            BitPruneExecutionRun runA = MainWspmBitPrune.execute(configAllFalse);
            BitPruneExecutionRun runB = MainWspmBitPrune.execute(configAllTrue);

            // 30. Results, patterns, supports, metadata, and projection content are identical
            assertEquals(runA.result().acceptedPatterns(), runB.result().acceptedPatterns(),
                    "Accepted patterns must be identical regardless of print flags");
            assertEquals(runA.result().supports(), runB.result().supports(),
                    "Pattern supports must be identical regardless of print flags");
            assertEquals(runA.metadata().distinctItemCount(), runB.metadata().distinctItemCount(),
                    "Metadata distinct item count must be identical regardless of print flags");
            assertEquals(runA.metadata().globalMeanWeight(), runB.metadata().globalMeanWeight(),
                    "Metadata global mean weight must be identical regardless of print flags");
            assertEquals(runA.metadata().minimumSupport().numberOfSequences(),
                    runB.metadata().minimumSupport().numberOfSequences(),
                    "Metadata sequence count must be identical regardless of print flags");
            assertEquals(runA.metadata().minimumSupport().configuredValue(),
                    runB.metadata().minimumSupport().configuredValue(),
                    "Metadata configured minsup must be identical regardless of print flags");
            assertEquals(runA.metadata().minimumSupport().mode(),
                    runB.metadata().minimumSupport().mode(),
                    "Metadata minsup mode must be identical regardless of print flags");
            assertEquals(runA.metadata().minimumSupport().relativeMinimumSupport(),
                    runB.metadata().minimumSupport().relativeMinimumSupport(),
                    "Metadata relative minsup must be identical regardless of print flags");
            assertEquals(runA.metadata().minimumSupport().effectiveThreshold(),
                    runB.metadata().minimumSupport().effectiveThreshold(),
                    "Metadata effective threshold must be identical regardless of print flags");
            assertEquals(runA.projection().content(), runB.projection().content(),
                    "Projection content must be identical regardless of print flags");
            assertEquals(runA.projection().lines(), runB.projection().lines(),
                    "Projection lines must be identical regardless of print flags");

            // Note: Per requirement 31, timing and memory measurements are NOT asserted for equality
        }
    }

    // =========================================================================
    // G. LIFECYCLE
    // =========================================================================

    @Nested
    @DisplayName("G. Sampler Lifecycle Tests")
    class LifecycleTests {

        @Test
        @DisplayName("32-34. ExecutionMemorySampler thread is terminated upon completion of execute()")
        void samplerThreadTerminatedAfterExecute() throws IOException {
            BitPruneRunConfiguration config = new BitPruneRunConfiguration(
                    TABLE1_PATH,
                    SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                    true,
                    0.14,
                    false,
                    false,
                    false,
                    tempDir.resolve("lifecycle_out.txt"));

            BitPruneExecutionRun run = MainWspmBitPrune.execute(config);
            assertNotNull(run);

            // 32-34. Check that no wspm-heap-sampler thread is left running
            // Bounded poll up to 2 seconds
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            boolean aliveSamplerFound = false;

            while (System.nanoTime() < deadline) {
                aliveSamplerFound = Thread.getAllStackTraces().keySet().stream()
                        .anyMatch(t -> t.getName().startsWith("wspm-heap-sampler-") && t.isAlive());
                if (!aliveSamplerFound) {
                    break;
                }
                Thread.onSpinWait();
            }

            assertFalse(aliveSamplerFound,
                    "No wspm-heap-sampler thread should remain alive after execute() completes");
        }
    }

    private static String sha256Hex(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data);
        return HexFormat.of().formatHex(digest);
    }
}
