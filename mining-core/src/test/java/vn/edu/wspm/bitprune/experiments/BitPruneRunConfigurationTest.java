package vn.edu.wspm.bitprune.experiments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportMode;
import vn.edu.wspm.io.SequenceDatabaseFormat;

/**
 * Unit and contract tests for {@link BitPruneRunConfiguration}.
 */
class BitPruneRunConfigurationTest {

    @TempDir
    Path temporaryDirectory;

    // --- A. Structural Contract ---

    @Test
    void isJavaRecordWithExactlyEightComponents() {
        assertTrue(BitPruneRunConfiguration.class.isRecord(),
                "BitPruneRunConfiguration must be a Java record");

        RecordComponent[] components = BitPruneRunConfiguration.class.getRecordComponents();
        assertNotNull(components);
        assertEquals(8, components.length, "BitPruneRunConfiguration must have exactly 8 components");

        Map<String, Class<?>> expectedComponents = Map.of(
                "datasetPath", Path.class,
                "inputFormat", SequenceDatabaseFormat.class,
                "minimumSupportIsRelative", boolean.class,
                "minimumSupport", double.class,
                "printPatterns", boolean.class,
                "printStatistics", boolean.class,
                "exportPatterns", boolean.class,
                "outputPath", Path.class);

        for (RecordComponent component : components) {
            String name = component.getName();
            assertTrue(expectedComponents.containsKey(name),
                    "Unexpected component name: " + name);
            assertEquals(expectedComponents.get(name), component.getType(),
                    "Type mismatch for component: " + name);
        }
    }

    @Test
    void recordHasNoRetentionComponentOrPolicy() {
        RecordComponent[] components = BitPruneRunConfiguration.class.getRecordComponents();
        boolean hasRetention = Arrays.stream(components)
                .anyMatch(c -> c.getName().toLowerCase().contains("retention")
                        || c.getType().getSimpleName().toLowerCase().contains("retention"));
        assertFalse(hasRetention,
                "BitPruneRunConfiguration must not contain any retention component");
    }

    // --- B. Valid Construction ---

    @Test
    void validConstructionWithTableOneRelativeConfiguration() {
        Path dataset = Path.of("datasets/table1.txt");
        Path output = Path.of("output/table1-out.txt");
        var config = new BitPruneRunConfiguration(
                dataset,
                SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                true,
                0.14,
                false,
                true,
                false,
                output);

        assertEquals(dataset, config.datasetPath());
        assertEquals(SequenceDatabaseFormat.SPMF_SEQUENTIAL, config.inputFormat());
        assertTrue(config.minimumSupportIsRelative());
        assertEquals(0.14, config.minimumSupport());
        assertFalse(config.printPatterns());
        assertTrue(config.printStatistics());
        assertFalse(config.exportPatterns());
        assertEquals(output, config.outputPath());
    }

    @Test
    void validConstructionWithSignRelativeConfiguration() {
        Path dataset = Path.of("datasets/SIGN.txt");
        Path output = Path.of("output/sign-out.txt");
        var config = new BitPruneRunConfiguration(
                dataset,
                SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                true,
                0.001,
                true,
                true,
                true,
                output);

        assertEquals(dataset, config.datasetPath());
        assertEquals(SequenceDatabaseFormat.SPMF_SEQUENTIAL, config.inputFormat());
        assertTrue(config.minimumSupportIsRelative());
        assertEquals(0.001, config.minimumSupport());
        assertTrue(config.printPatterns());
        assertTrue(config.printStatistics());
        assertTrue(config.exportPatterns());
        assertEquals(output, config.outputPath());
    }

    @Test
    void validConstructionWithAbsoluteConfiguration() {
        Path dataset = Path.of("datasets/custom.txt");
        Path output = Path.of("output/custom-out.txt");
        var config = new BitPruneRunConfiguration(
                dataset,
                SequenceDatabaseFormat.FIMI_TRANSACTION,
                false,
                0.98,
                false,
                false,
                false,
                output);

        assertEquals(dataset, config.datasetPath());
        assertEquals(SequenceDatabaseFormat.FIMI_TRANSACTION, config.inputFormat());
        assertFalse(config.minimumSupportIsRelative());
        assertEquals(0.98, config.minimumSupport());
        assertFalse(config.printPatterns());
        assertFalse(config.printStatistics());
        assertFalse(config.exportPatterns());
        assertEquals(output, config.outputPath());
    }

    @Test
    void preservesAllBooleanCombinationsFaithfully() {
        Path path = Path.of("dummy.txt");
        boolean[] flags = {false, true};

        for (boolean rel : flags) {
            for (boolean printPat : flags) {
                for (boolean printStat : flags) {
                    for (boolean exportPat : flags) {
                        double minSup = rel ? 0.5 : 5.0;
                        var config = new BitPruneRunConfiguration(
                                path,
                                SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                                rel,
                                minSup,
                                printPat,
                                printStat,
                                exportPat,
                                path);

                        assertEquals(rel, config.minimumSupportIsRelative());
                        assertEquals(printPat, config.printPatterns());
                        assertEquals(printStat, config.printStatistics());
                        assertEquals(exportPat, config.exportPatterns());
                    }
                }
            }
        }
    }

    // --- C. Null Validation ---

    @Test
    void rejectsNullDatasetPath() {
        NullPointerException ex = assertThrows(NullPointerException.class, () ->
                new BitPruneRunConfiguration(
                        null,
                        SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                        true,
                        0.14,
                        false,
                        true,
                        false,
                        Path.of("output.txt")));
        assertTrue(ex.getMessage().contains("datasetPath"));
    }

    @Test
    void rejectsNullInputFormat() {
        NullPointerException ex = assertThrows(NullPointerException.class, () ->
                new BitPruneRunConfiguration(
                        Path.of("dataset.txt"),
                        null,
                        true,
                        0.14,
                        false,
                        true,
                        false,
                        Path.of("output.txt")));
        assertTrue(ex.getMessage().contains("inputFormat"));
    }

    @Test
    void rejectsNullOutputPathEvenWhenExportPatternsIsFalse() {
        NullPointerException ex = assertThrows(NullPointerException.class, () ->
                new BitPruneRunConfiguration(
                        Path.of("dataset.txt"),
                        SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                        true,
                        0.14,
                        false,
                        true,
                        false,
                        null));
        assertTrue(ex.getMessage().contains("outputPath"));
    }

    @Test
    void rejectsNullOutputPathWhenExportPatternsIsTrue() {
        NullPointerException ex = assertThrows(NullPointerException.class, () ->
                new BitPruneRunConfiguration(
                        Path.of("dataset.txt"),
                        SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                        true,
                        0.14,
                        false,
                        true,
                        true,
                        null));
        assertTrue(ex.getMessage().contains("outputPath"));
    }

    // --- D. Relative Validation ---

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.001, 0.14, 0.5, 1.0})
    void acceptsValidRelativeFractions(double relativeMinSup) {
        var config = new BitPruneRunConfiguration(
                Path.of("dataset.txt"),
                SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                true,
                relativeMinSup,
                false,
                true,
                false,
                Path.of("output.txt"));
        assertEquals(relativeMinSup, config.minimumSupport());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.0001, -0.14, -1.0, 1.0001, 1.5, 14.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidRelativeValues(double invalidMinSup) {
        assertThrows(IllegalArgumentException.class, () ->
                new BitPruneRunConfiguration(
                        Path.of("dataset.txt"),
                        SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                        true,
                        invalidMinSup,
                        false,
                        true,
                        false,
                        Path.of("output.txt")));
    }

    // --- E. Absolute Validation ---

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.98, 1.0, 50.0, 1000.0, 100000.0})
    void acceptsValidAbsoluteThresholds(double absoluteMinSup) {
        var config = new BitPruneRunConfiguration(
                Path.of("dataset.txt"),
                SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                false,
                absoluteMinSup,
                false,
                true,
                false,
                Path.of("output.txt"));
        assertEquals(absoluteMinSup, config.minimumSupport());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.0001, -0.98, -10.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidAbsoluteValues(double invalidMinSup) {
        assertThrows(IllegalArgumentException.class, () ->
                new BitPruneRunConfiguration(
                        Path.of("dataset.txt"),
                        SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                        false,
                        invalidMinSup,
                        false,
                        true,
                        false,
                        Path.of("output.txt")));
    }

    // --- F. Conversion ---

    @Test
    void convertsRelativeFourteenHundredthsToFourteenPercent() {
        var config = new BitPruneRunConfiguration(
                Path.of("dataset.txt"),
                SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                true,
                0.14,
                false,
                true,
                false,
                Path.of("output.txt"));

        MinimumSupport minSup = config.toMinimumSupport();
        assertEquals(MinimumSupportMode.PERCENT, minSup.mode());
        assertEquals(14.0, minSup.configuredValue());
    }

    @Test
    void convertsRelativeOneThousandthToOneTenthPercent() {
        var config = new BitPruneRunConfiguration(
                Path.of("dataset.txt"),
                SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                true,
                0.001,
                false,
                true,
                false,
                Path.of("output.txt"));

        MinimumSupport minSup = config.toMinimumSupport();
        assertEquals(MinimumSupportMode.PERCENT, minSup.mode());
        assertEquals(0.1, minSup.configuredValue());
    }

    @Test
    void convertsRelativeZeroToZeroPercent() {
        var config = new BitPruneRunConfiguration(
                Path.of("dataset.txt"),
                SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                true,
                0.0,
                false,
                true,
                false,
                Path.of("output.txt"));

        MinimumSupport minSup = config.toMinimumSupport();
        assertEquals(MinimumSupportMode.PERCENT, minSup.mode());
        assertEquals(0.0, minSup.configuredValue());
    }

    @Test
    void convertsRelativeOneToOneHundredPercent() {
        var config = new BitPruneRunConfiguration(
                Path.of("dataset.txt"),
                SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                true,
                1.0,
                false,
                true,
                false,
                Path.of("output.txt"));

        MinimumSupport minSup = config.toMinimumSupport();
        assertEquals(MinimumSupportMode.PERCENT, minSup.mode());
        assertEquals(100.0, minSup.configuredValue());
    }

    @Test
    void convertsAbsoluteThresholdDirectlyWithoutRounding() {
        var config = new BitPruneRunConfiguration(
                Path.of("dataset.txt"),
                SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                false,
                0.98,
                false,
                true,
                false,
                Path.of("output.txt"));

        MinimumSupport minSup = config.toMinimumSupport();
        assertEquals(MinimumSupportMode.ABSOLUTE, minSup.mode());
        assertEquals(0.98, minSup.configuredValue());
    }

    @Test
    void convertsAbsoluteZeroDirectly() {
        var config = new BitPruneRunConfiguration(
                Path.of("dataset.txt"),
                SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                false,
                0.0,
                false,
                true,
                false,
                Path.of("output.txt"));

        MinimumSupport minSup = config.toMinimumSupport();
        assertEquals(MinimumSupportMode.ABSOLUTE, minSup.mode());
        assertEquals(0.0, minSup.configuredValue());
    }

    @Test
    void conversionIsDeterministicAcrossRepeatedCalls() {
        var config = new BitPruneRunConfiguration(
                Path.of("dataset.txt"),
                SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                true,
                0.14,
                false,
                true,
                false,
                Path.of("output.txt"));

        MinimumSupport first = config.toMinimumSupport();
        MinimumSupport second = config.toMinimumSupport();
        assertEquals(first, second);
        assertEquals(first.mode(), second.mode());
        assertEquals(first.configuredValue(), second.configuredValue());
    }

    // --- G. No Filesystem Validation Side-effects ---

    @Test
    void constructionDoesNotValidateOrTouchFilesystem() {
        Path nonExistentDataset = temporaryDirectory.resolve("does_not_exist/dataset.txt");
        Path nonExistentOutputParent = temporaryDirectory.resolve("does_not_exist/output/result.txt");
        Path samePath = temporaryDirectory.resolve("same_path.txt");

        assertFalse(Files.exists(nonExistentDataset));
        assertFalse(Files.exists(nonExistentOutputParent.getParent()));
        assertFalse(Files.exists(samePath));

        // Case 1: non-existent dataset and non-existent output parent
        var config1 = new BitPruneRunConfiguration(
                nonExistentDataset,
                SequenceDatabaseFormat.SPMF_SEQUENTIAL,
                true,
                0.14,
                false,
                true,
                true,
                nonExistentOutputParent);
        assertEquals(nonExistentDataset, config1.datasetPath());
        assertEquals(nonExistentOutputParent, config1.outputPath());

        // Case 2: datasetPath == outputPath
        var config2 = new BitPruneRunConfiguration(
                samePath,
                SequenceDatabaseFormat.FIMI_TRANSACTION,
                false,
                1.0,
                true,
                true,
                true,
                samePath);
        assertEquals(samePath, config2.datasetPath());
        assertEquals(samePath, config2.outputPath());

        // Verify no files or directories were created as side effects of record construction
        assertFalse(Files.exists(nonExistentDataset), "dataset must not be created");
        assertFalse(Files.exists(nonExistentOutputParent.getParent()), "output parent must not be created");
        assertFalse(Files.exists(samePath), "samePath must not be created");
    }
}
