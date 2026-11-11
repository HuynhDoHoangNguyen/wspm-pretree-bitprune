package vn.edu.wspm.backend.services.dataset.conversion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversionArtifactStorageTest {
    @TempDir Path root;
    private final String id = UUID.randomUUID().toString(), attempt = UUID.randomUUID().toString();
    private final byte[] spmf = "1 -1 -2\n".getBytes(StandardCharsets.UTF_8), mapping = "{}\n".getBytes(StandardCharsets.UTF_8);
    private ConversionArtifactStorage storage;
    @BeforeEach void snapshot() throws Exception {
        Files.createDirectory(root.resolve(id)); Files.writeString(root.resolve(id).resolve("raw.csv"), "original");
        storage = new ConversionArtifactStorage(root.toString(), 1024);
    }
    private Path directory() { return root.resolve(id).resolve("conversions").resolve(attempt); }

    @Test void exclusiveWriteReadHashAndReceiptCleanupLeaveRawAndSharedParentAlone() throws Exception {
        var receipt = storage.save(id, attempt, spmf, mapping);
        assertArrayEquals(spmf, storage.read(id, attempt, "sequences.spmf", receipt.sequence()));
        assertArrayEquals(mapping, storage.read(id, attempt, "mapping.json", receipt.mapping()));
        assertThrows(IOException.class, () -> storage.save(id, attempt, spmf, mapping));
        assertArrayEquals(spmf, Files.readAllBytes(directory().resolve("sequences.spmf")));
        storage.discard(receipt);
        assertFalse(Files.exists(directory()));
        assertEquals("original", Files.readString(root.resolve(id).resolve("raw.csv")));
        assertTrue(Files.isDirectory(root.resolve(id).resolve("conversions")));
    }
    @Test void tamperMissingAndDescriptorTraversalAreRejected() throws Exception {
        var receipt = storage.save(id, attempt, spmf, mapping);
        Files.writeString(directory().resolve("sequences.spmf"), "2 -1 -2\n");
        assertThrows(IOException.class, () -> storage.read(id, attempt, "sequences.spmf", receipt.sequence()));
        Files.delete(directory().resolve("mapping.json"));
        assertThrows(IOException.class, () -> storage.read(id, attempt, "mapping.json", receipt.mapping()));
        var descriptor = new ConversionArtifactResult.ArtifactFile("../private", receipt.sequence().sha256(), spmf.length);
        assertThrows(IOException.class, () -> storage.read(id, attempt, "sequences.spmf", descriptor));
        assertThrows(IOException.class, () -> storage.read("../escape", attempt, "sequences.spmf", receipt.sequence()));
        assertThrows(IOException.class, () -> storage.read(id, attempt, "raw.csv", receipt.sequence()));
    }
    @Test void secondFileFailureCleansOnlyTheNewAttempt() throws Exception {
        var failing = new ConversionArtifactStorage(root.toString(), 1024) {
            @Override protected void write(Receipt receipt, String name, byte[] bytes) throws IOException {
                if (name.equals("mapping.json")) throw new IOException("Injected second-file failure");
                super.write(receipt, name, bytes);
            }
        };
        assertThrows(IOException.class, () -> failing.save(id, attempt, spmf, mapping));
        assertFalse(Files.exists(directory()));
        assertEquals("original", Files.readString(root.resolve(id).resolve("raw.csv")));
    }
    @Test void foreignReceiptAndUnexpectedEntriesAreNeverRecursivelyDeleted() throws Exception {
        var receipt = storage.save(id, attempt, spmf, mapping);
        assertThrows(IllegalArgumentException.class, () -> new ConversionArtifactStorage(root.toString(), 1024).discard(receipt));
        Files.writeString(directory().resolve("unowned.txt"), "keep");
        storage.discard(receipt);
        assertEquals("keep", Files.readString(directory().resolve("unowned.txt")));
        assertEquals("original", Files.readString(root.resolve(id).resolve("raw.csv")));
    }
    @Test void byteBudgetsAndInvalidConfigurationFailBeforeAllocation() {
        assertThrows(IOException.class, () -> new ConversionArtifactStorage(root.toString(), 1).save(id, attempt, spmf, mapping));
        assertFalse(Files.exists(directory()));
        assertThrows(IllegalArgumentException.class, () -> new ConversionArtifactStorage(root.toString(), 0));
        assertThrows(IllegalArgumentException.class, () -> new ConversionArtifactStorage(root.toString(), Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> new ConversionArtifactStorage("relative", 1024));
        assertThrows(IllegalArgumentException.class, () -> new ConversionArtifactStorage(System.getProperty("user.dir"), 1024));
    }
}
