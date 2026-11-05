package vn.edu.wspm.backend.services.dataset;

import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.wspm.backend.exception.ApplicationException;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class RawSnapshotStorageTest {
    @TempDir Path temporary;

    RawSnapshotStorage storage(Path path, long max) {
        return new RawSnapshotStorage(path.toString(), new MultipartConfigElement("", max, max + 1024, 0));
    }

    @Test
    void preservesBomNewlinesAndArbitraryBytesAndUsesExclusiveGeneratedNames() throws Exception {
        var storage = storage(temporary, 100);
        byte[] bytes = {(byte) 0xef, (byte) 0xbb, (byte) 0xbf, 'a', '\r', '\n', 'b', '\n', (byte) 0xff};
        var first = storage.save(new ByteArrayInputStream(bytes));
        var second = storage.save(new ByteArrayInputStream(bytes));
        assertNotEquals(first.snapshotId(), second.snapshotId());
        assertEquals(UUID.fromString(first.snapshotId()).toString(), first.snapshotId());
        assertEquals(first.snapshotId() + "/raw.csv", first.reference());
        assertArrayEquals(bytes, Files.readAllBytes(temporary.resolve(first.reference())));
        assertArrayEquals(bytes, Files.readAllBytes(temporary.resolve(second.reference())));
        assertEquals(bytes.length, first.sizeBytes());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), first.sha256());
        assertEquals(first.sha256(), second.sha256());
        assertFalse(first.toString().contains(temporary.toString()));
    }

    @Test
    void streamLimitAndReadFailuresRemoveOnlyTheirNewAllocation() throws Exception {
        var storage = storage(temporary, 2);
        var existing = storage.save(new ByteArrayInputStream(new byte[]{1, 2}));
        assertEquals(413, assertThrows(ApplicationException.class,
                () -> storage.save(new ByteArrayInputStream(new byte[]{1, 2, 3}))).status().value());
        assertEquals(400, assertThrows(ApplicationException.class,
                () -> storage.save(new ByteArrayInputStream(new byte[0]))).status().value());
        assertThrows(IOException.class, () -> storage.save(new InputStream() {
            @Override public int read() throws IOException { throw new IOException("synthetic private path"); }
        }));
        try (var entries = Files.list(temporary)) { assertEquals(1, entries.count()); }
        assertArrayEquals(new byte[]{1, 2}, Files.readAllBytes(temporary.resolve(existing.reference())));
    }

    @Test
    void concurrentAllocationsDoNotOverwriteOrShareFiles() throws Exception {
        var storage = storage(temporary, 10);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<RawSnapshotStorage.StoredRaw>> uploads = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                byte value = (byte) i;
                uploads.add(executor.submit(() -> storage.save(new ByteArrayInputStream(new byte[]{value}))));
            }
            var ids = new HashSet<String>();
            for (int i = 0; i < uploads.size(); i++) {
                var raw = uploads.get(i).get();
                assertTrue(ids.add(raw.snapshotId()));
                assertArrayEquals(new byte[]{(byte) i}, Files.readAllBytes(temporary.resolve(raw.reference())));
            }
        }
    }

    @Test
    void cleanupRequiresReceiptOwnershipAndPreservesUnexpectedEntries() throws Exception {
        var storage = storage(temporary, 10);
        var raw = storage.save(new ByteArrayInputStream(new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> storage(temporary, 10).discard(raw));
        Path unexpected = temporary.resolve(raw.snapshotId()).resolve("unowned.txt");
        Files.writeString(unexpected, "preserve");
        storage.discard(raw);
        assertEquals("preserve", Files.readString(unexpected));
        assertFalse(Files.exists(temporary.resolve(raw.reference())));
    }

    @Test
    void rejectsRelativeOrApplicationRootsAndNonDirectoryAncestors() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> storage(Path.of("relative"), 10));
        assertThrows(IllegalArgumentException.class, () -> storage(Path.of(System.getProperty("user.dir")), 10));
        Path blocker = temporary.resolve("file");
        Files.writeString(blocker, "untouched");
        assertThrows(IOException.class, () -> storage(blocker.resolve("root"), 10)
                .save(new ByteArrayInputStream(new byte[]{1})));
        assertEquals("untouched", Files.readString(blocker));
    }
}
