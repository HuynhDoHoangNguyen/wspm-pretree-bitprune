package vn.edu.wspm.bitprune.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.wspm.bitprune.result.BitPruneMiningResult;
import vn.edu.wspm.bitprune.result.BitPruneOutputProjection;
import vn.edu.wspm.bitprune.statistics.BitPruneStatistics;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.tree.ExtensionType;
import vn.edu.wspm.tree.PrefixTree;

/**
 * Unit and boundary contract tests for {@link BitPruneOutputWriter} (C14.2).
 *
 * <p>Validates API contract, null validation order, exact UTF-8 byte emission without BOM,
 * LF preservation, parent directory creation, existing-file truncation, empty output zero-byte handling,
 * resource cleanup, and failure propagation.</p>
 */
class BitPruneOutputWriterTest {

    @TempDir
    Path temporaryDirectory;

    private final BitPruneOutputWriter writer = new BitPruneOutputWriter();

    private final Item item1 = new Item(1);
    private final Item item2 = new Item(2);

    private final Pattern p1 = Pattern.singleton(item1);
    private final Pattern p2 = Pattern.singleton(item2);
    private final Pattern p12I = Pattern.of(Itemset.of(item1, item2));
    private final Pattern p1_2S = Pattern.of(Itemset.of(item1), Itemset.of(item2));

    private BitPruneStatistics createValidStatistics(long f1Count, long iExt, long sExt) {
        long ext = iExt + sExt;
        return new BitPruneStatistics(
                7L,
                f1Count,
                ext,
                iExt,
                sExt,
                0L,
                0L,
                0L,
                ext,
                f1Count,
                f1Count + ext,
                ext);
    }

    private BitPruneOutputProjection createSingletonProjection() {
        PrefixTree tree = new PrefixTree();
        tree.insert(Pattern.empty(), p1, ExtensionType.S_EXTENSION);
        tree.freeze();

        Map<Pattern, Integer> supports = Map.of(p1, 7);
        BitPruneMiningResult result = new BitPruneMiningResult(
                tree,
                supports,
                createValidStatistics(1, 0, 0));
        return BitPruneOutputProjection.from(result);
    }

    private BitPruneOutputProjection createIExtensionProjection() {
        PrefixTree tree = new PrefixTree();
        tree.insert(Pattern.empty(), p1, ExtensionType.S_EXTENSION);
        tree.insert(p1, p12I, ExtensionType.I_EXTENSION);
        tree.freeze();

        Map<Pattern, Integer> supports = new LinkedHashMap<>();
        supports.put(p1, 7);
        supports.put(p12I, 7);

        BitPruneMiningResult result = new BitPruneMiningResult(
                tree,
                supports,
                createValidStatistics(1, 1, 0));
        return BitPruneOutputProjection.from(result);
    }

    private BitPruneOutputProjection createSExtensionProjection() {
        PrefixTree tree = new PrefixTree();
        tree.insert(Pattern.empty(), p1, ExtensionType.S_EXTENSION);
        tree.insert(p1, p1_2S, ExtensionType.S_EXTENSION);
        tree.freeze();

        Map<Pattern, Integer> supports = new LinkedHashMap<>();
        supports.put(p1, 7);
        supports.put(p1_2S, 7);

        BitPruneMiningResult result = new BitPruneMiningResult(
                tree,
                supports,
                createValidStatistics(1, 0, 1));
        return BitPruneOutputProjection.from(result);
    }

    private BitPruneOutputProjection createMixedProjection() {
        PrefixTree tree = new PrefixTree();
        tree.insert(Pattern.empty(), p1, ExtensionType.S_EXTENSION);
        tree.insert(p1, p12I, ExtensionType.I_EXTENSION);
        tree.insert(p1, p1_2S, ExtensionType.S_EXTENSION);
        tree.insert(Pattern.empty(), p2, ExtensionType.S_EXTENSION);
        tree.freeze();

        Map<Pattern, Integer> supports = new LinkedHashMap<>();
        supports.put(p1, 7);
        supports.put(p12I, 7);
        supports.put(p1_2S, 6);
        supports.put(p2, 5);

        BitPruneMiningResult result = new BitPruneMiningResult(
                tree,
                supports,
                createValidStatistics(2, 1, 1));
        return BitPruneOutputProjection.from(result);
    }

    private BitPruneOutputProjection createEmptyProjection() {
        PrefixTree tree = new PrefixTree();
        tree.freeze();

        BitPruneMiningResult result = new BitPruneMiningResult(
                tree,
                Map.of(),
                createValidStatistics(0, 0, 0));
        return BitPruneOutputProjection.from(result);
    }

    @Test
    @DisplayName("1. BitPruneOutputWriter class is final, stateless, and has expected public write method")
    void testClassContract() throws NoSuchMethodException {
        Class<BitPruneOutputWriter> clazz = BitPruneOutputWriter.class;
        assertTrue(Modifier.isPublic(clazz.getModifiers()), "class must be public");
        assertTrue(Modifier.isFinal(clazz.getModifiers()), "class must be final");
        assertEquals(0, clazz.getDeclaredFields().length, "writer must have no declared fields (stateless)");

        Method writeMethod = clazz.getMethod("write", Path.class, BitPruneOutputProjection.class);
        assertTrue(Modifier.isPublic(writeMethod.getModifiers()), "write method must be public");
        assertEquals(void.class, writeMethod.getReturnType(), "write method must return void");
    }

    @Test
    @DisplayName("2. BitPruneOutputWriter.write rejects null outputPath")
    void testWriteRejectsNullOutputPath() {
        BitPruneOutputProjection projection = createSingletonProjection();
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> writer.write(null, projection));
        assertTrue(ex.getMessage().contains("outputPath must not be null"));
    }

    @Test
    @DisplayName("3. BitPruneOutputWriter.write rejects null projection")
    void testWriteRejectsNullProjection() {
        Path output = temporaryDirectory.resolve("output.txt");
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> writer.write(output, null));
        assertTrue(ex.getMessage().contains("projection must not be null"));
    }

    @Test
    @DisplayName("4. Validation occurs before any filesystem mutation")
    void testValidationOccursBeforeFilesystemMutation() {
        Path uncreatedDir = temporaryDirectory.resolve("uncreated_parent");
        Path output = uncreatedDir.resolve("target.txt");

        assertThrows(NullPointerException.class, () -> writer.write(output, null));
        assertFalse(Files.exists(uncreatedDir), "parent directory must not be created on validation failure");
    }

    @Test
    @DisplayName("5. Write singleton pattern writes exact UTF-8 bytes without BOM")
    void testWriteSingletonPatternExactBytes() throws IOException {
        BitPruneOutputProjection projection = createSingletonProjection();
        Path output = temporaryDirectory.resolve("singleton.txt");

        writer.write(output, projection);

        byte[] actualBytes = Files.readAllBytes(output);
        byte[] expectedBytes = "<(1)> #SUP: 7\n".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(expectedBytes, actualBytes);
        assertArrayEquals(projection.content().getBytes(StandardCharsets.UTF_8), actualBytes);

        // Verify no UTF-8 BOM (0xEF, 0xBB, 0xBF)
        assertFalse(actualBytes.length >= 3
                && (actualBytes[0] & 0xFF) == 0xEF
                && (actualBytes[1] & 0xFF) == 0xBB
                && (actualBytes[2] & 0xFF) == 0xBF, "must not include UTF-8 BOM");
    }

    @Test
    @DisplayName("6. Write I-extension pattern writes exact UTF-8 bytes")
    void testWriteIExtensionPatternExactBytes() throws IOException {
        BitPruneOutputProjection projection = createIExtensionProjection();
        Path output = temporaryDirectory.resolve("iext.txt");

        writer.write(output, projection);

        byte[] actualBytes = Files.readAllBytes(output);
        byte[] expectedBytes = "<(1)> #SUP: 7\n<(1 2)> #SUP: 7\n".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(expectedBytes, actualBytes);
        assertArrayEquals(projection.content().getBytes(StandardCharsets.UTF_8), actualBytes);
        assertTrue(Files.readString(output, StandardCharsets.UTF_8).contains("<(1 2)> #SUP: 7\n"));
    }

    @Test
    @DisplayName("7. Write S-extension pattern writes exact UTF-8 bytes")
    void testWriteSExtensionPatternExactBytes() throws IOException {
        BitPruneOutputProjection projection = createSExtensionProjection();
        Path output = temporaryDirectory.resolve("sext.txt");

        writer.write(output, projection);

        byte[] actualBytes = Files.readAllBytes(output);
        byte[] expectedBytes = "<(1)> #SUP: 7\n<(1)(2)> #SUP: 7\n".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(expectedBytes, actualBytes);
        assertArrayEquals(projection.content().getBytes(StandardCharsets.UTF_8), actualBytes);
        assertTrue(Files.readString(output, StandardCharsets.UTF_8).contains("<(1)(2)> #SUP: 7\n"));
    }

    @Test
    @DisplayName("8. Write multiple mixed patterns preserves deterministic PrefixTree order, LF only, and single trailing LF")
    void testWriteMultipleMixedPatterns() throws IOException {
        BitPruneOutputProjection projection = createMixedProjection();
        Path output = temporaryDirectory.resolve("mixed.txt");

        writer.write(output, projection);

        byte[] actualBytes = Files.readAllBytes(output);
        assertArrayEquals(projection.content().getBytes(StandardCharsets.UTF_8), actualBytes);

        String readString = Files.readString(output, StandardCharsets.UTF_8);
        assertFalse(readString.contains("\r"), "content must be LF only without CR");
        assertTrue(readString.endsWith("\n"), "non-empty output must end with trailing LF");
        assertFalse(readString.endsWith("\n\n"), "must have exactly one trailing LF");

        List<String> actualLines = Files.readAllLines(output, StandardCharsets.UTF_8);
        assertEquals(projection.lines(), actualLines);
        assertEquals(List.of(
                "<(1)> #SUP: 7",
                "<(1 2)> #SUP: 7",
                "<(1)(2)> #SUP: 6",
                "<(2)> #SUP: 5"), actualLines);
    }

    @Test
    @DisplayName("9. Write empty projection creates zero-byte file when missing")
    void testWriteEmptyProjectionCreatesZeroByteFileWhenMissing() throws IOException {
        BitPruneOutputProjection projection = createEmptyProjection();
        Path output = temporaryDirectory.resolve("empty_missing.txt");

        writer.write(output, projection);

        assertTrue(Files.isRegularFile(output), "output must be created as a regular file");
        assertEquals(0L, Files.size(output), "empty projection must produce exactly zero bytes");
        assertArrayEquals(new byte[0], Files.readAllBytes(output));
    }

    @Test
    @DisplayName("10. Write empty projection truncates existing non-empty file to zero bytes")
    void testWriteEmptyProjectionTruncatesExistingFile() throws IOException {
        BitPruneOutputProjection emptyProjection = createEmptyProjection();
        Path output = temporaryDirectory.resolve("empty_existing.txt");

        Files.writeString(output, "pre-existing content that should be cleared\n", StandardCharsets.UTF_8);
        assertTrue(Files.size(output) > 0);

        writer.write(output, emptyProjection);

        assertTrue(Files.isRegularFile(output));
        assertEquals(0L, Files.size(output), "existing file must be truncated to zero bytes");
        assertArrayEquals(new byte[0], Files.readAllBytes(output));
    }

    @Test
    @DisplayName("11. Write replaces existing longer file completely without appending or stale suffix")
    void testWriteReplacesLongerExistingContentWithoutStaleSuffix() throws IOException {
        BitPruneOutputProjection mixedProjection = createMixedProjection();
        BitPruneOutputProjection singletonProjection = createSingletonProjection();
        Path output = temporaryDirectory.resolve("replace.txt");

        // First write larger projection
        writer.write(output, mixedProjection);
        byte[] firstBytes = Files.readAllBytes(output);
        assertArrayEquals(mixedProjection.content().getBytes(StandardCharsets.UTF_8), firstBytes);

        // Second write smaller projection to the same file
        writer.write(output, singletonProjection);
        byte[] secondBytes = Files.readAllBytes(output);

        assertArrayEquals(singletonProjection.content().getBytes(StandardCharsets.UTF_8), secondBytes);
        assertEquals("<(1)> #SUP: 7\n", Files.readString(output, StandardCharsets.UTF_8));
        assertTrue(secondBytes.length < firstBytes.length, "second file should be shorter than first");
    }

    @Test
    @DisplayName("12. Repeated writes of same projection produce identical bytes")
    void testRepeatedWritesProduceIdenticalBytes() throws IOException {
        BitPruneOutputProjection projection = createMixedProjection();
        Path output = temporaryDirectory.resolve("repeated.txt");

        writer.write(output, projection);
        byte[] firstRunBytes = Files.readAllBytes(output);

        writer.write(output, projection);
        byte[] secondRunBytes = Files.readAllBytes(output);

        assertArrayEquals(firstRunBytes, secondRunBytes);
    }

    @Test
    @DisplayName("13. Write creates missing nested parent directories recursively")
    void testWriteCreatesMissingNestedDirectories() throws IOException {
        BitPruneOutputProjection projection = createSingletonProjection();
        Path nestedOutput = temporaryDirectory
                .resolve("level1")
                .resolve("level2")
                .resolve("level3")
                .resolve("nested_patterns.txt");

        assertFalse(Files.exists(nestedOutput.getParent()));

        writer.write(nestedOutput, projection);

        assertTrue(Files.exists(nestedOutput));
        assertTrue(Files.isDirectory(nestedOutput.getParent()));
        assertArrayEquals(projection.content().getBytes(StandardCharsets.UTF_8), Files.readAllBytes(nestedOutput));
    }

    @Test
    @DisplayName("14. Write reuses existing parent directory without error")
    void testWriteReusesExistingParentDirectory() throws IOException {
        BitPruneOutputProjection projection = createSingletonProjection();
        Path existingDir = Files.createDirectory(temporaryDirectory.resolve("existing_parent"));
        Path output = existingDir.resolve("output.txt");

        writer.write(output, projection);

        assertTrue(Files.exists(output));
        assertArrayEquals(projection.content().getBytes(StandardCharsets.UTF_8), Files.readAllBytes(output));
    }

    @Test
    @DisplayName("15. Write propagates IOException when parent path is an existing regular file")
    void testWriteFailsWhenParentIsRegularFile() throws IOException {
        BitPruneOutputProjection projection = createSingletonProjection();
        Path fileAsParent = temporaryDirectory.resolve("parent_is_file.txt");
        Files.writeString(fileAsParent, "I am a file, not a directory", StandardCharsets.UTF_8);

        Path invalidChild = fileAsParent.resolve("child.txt");

        assertThrows(IOException.class, () -> writer.write(invalidChild, projection));
    }

    @Test
    @DisplayName("16. Write propagates IOException when output path is an existing directory")
    void testWriteFailsWhenOutputIsDirectory() throws IOException {
        BitPruneOutputProjection projection = createSingletonProjection();
        Path dirAsOutput = Files.createDirectory(temporaryDirectory.resolve("output_is_directory"));

        assertThrows(IOException.class, () -> writer.write(dirAsOutput, projection));
    }

    @Test
    @DisplayName("17. Write supports filename with Unicode characters and spaces")
    void testWriteSupportsUnicodeAndSpacesInFilename() throws IOException {
        BitPruneOutputProjection projection = createSingletonProjection();
        Path unicodePath = temporaryDirectory.resolve("mẫu tuần tự có trọng số 2026.txt");

        writer.write(unicodePath, projection);

        assertTrue(Files.exists(unicodePath));
        assertArrayEquals(projection.content().getBytes(StandardCharsets.UTF_8), Files.readAllBytes(unicodePath));
    }

    @Test
    @DisplayName("18. Write normalizes path segments within temporaryDirectory")
    void testWriteNormalizesPath() throws IOException {
        BitPruneOutputProjection projection = createSingletonProjection();
        Path subDir = Files.createDirectory(temporaryDirectory.resolve("sub_folder"));
        Path unnormalizedPath = subDir.resolve("..").resolve("sub_folder").resolve("normalized.txt");

        writer.write(unnormalizedPath, projection);

        Path canonicalOutput = subDir.resolve("normalized.txt");
        assertTrue(Files.exists(canonicalOutput));
        assertArrayEquals(projection.content().getBytes(StandardCharsets.UTF_8), Files.readAllBytes(canonicalOutput));
    }

    @Test
    @DisplayName("19. Write preserves projection immutability and content integrity")
    void testWritePreservesProjectionIntegrity() throws IOException {
        BitPruneOutputProjection projection = createMixedProjection();
        String originalContent = projection.content();
        List<String> originalLines = projection.lines();
        int originalSize = projection.entries().size();

        Path output = temporaryDirectory.resolve("integrity.txt");
        writer.write(output, projection);

        assertEquals(originalContent, projection.content(), "projection content must remain unchanged");
        assertEquals(originalLines, projection.lines(), "projection lines must remain unchanged");
        assertEquals(originalSize, projection.entries().size(), "projection size must remain unchanged");
    }

    @Test
    @DisplayName("20. Proper resource cleanup allows immediate file read, move, and deletion")
    void testResourceCleanupAllowsFileMoveAndDelete() throws IOException {
        BitPruneOutputProjection projection = createSingletonProjection();
        Path output = temporaryDirectory.resolve("lifecycle_test.txt");

        writer.write(output, projection);

        // Immediate read
        byte[] readBytes = Files.readAllBytes(output);
        assertNotNull(readBytes);

        // Immediate move
        Path movedOutput = temporaryDirectory.resolve("lifecycle_moved.txt");
        Files.move(output, movedOutput);
        assertFalse(Files.exists(output));
        assertTrue(Files.exists(movedOutput));

        // Immediate delete
        Files.delete(movedOutput);
        assertFalse(Files.exists(movedOutput));
    }
}
