package vn.edu.wspm.backend.services.dataset.conversion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactResult.ArtifactFile;

/** Private exclusive attempt storage. Never overwrites, enumerates or recursively deletes a snapshot. */
@Component
@Slf4j
public class ConversionArtifactStorage {
    private final Path root;
    private final long maxBytes;
    public ConversionArtifactStorage(@Value("${wdm.snapshot-storage.root}") String root,
            @Value("${wdm.conversion.max-artifact-bytes:67108864}") long maxBytes) {
        this.root = Path.of(root).normalize(); this.maxBytes = maxBytes;
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path project = working.getFileName() != null && "backend".equals(working.getFileName().toString()) ? working.getParent() : working;
        if (!this.root.isAbsolute() || this.root.startsWith(project) || maxBytes <= 0 || maxBytes > Integer.MAX_VALUE - 8L) {
            throw new IllegalArgumentException("Conversion storage requires a private absolute root and bounded positive byte limit");
        }
    }

    public Receipt save(String id, String attempt, byte[] sequence, byte[] mapping) throws IOException {
        if (sequence.length == 0 || mapping.length == 0 || sequence.length > maxBytes || mapping.length > maxBytes) {
            throw new IOException("Conversion artifact exceeds storage budget");
        }
        Path directory = attemptPath(id, attempt);
        check(directory.getParent().getParent()); // snapshot must already exist
        if (!Files.isDirectory(directory.getParent().getParent(), LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing snapshot directory");
        try { Files.createDirectory(directory.getParent()); } catch (FileAlreadyExistsException existing) { check(directory.getParent()); }
        check(directory.getParent());
        Files.createDirectory(directory); // exclusive ownership; never clean a pre-existing attempt
        var receipt = new Receipt(this, id, attempt, directory, attributes(directory));
        try {
            write(receipt, "sequences.spmf", sequence);
            write(receipt, "mapping.json", mapping);
            receipt.sequence = descriptor(id, attempt, "sequences.spmf", sequence);
            receipt.mapping = descriptor(id, attempt, "mapping.json", mapping);
            return receipt;
        } catch (IOException | RuntimeException failure) { discard(receipt); throw failure; }
    }

    protected void write(Receipt receipt, String name, byte[] bytes) throws IOException {
        check(receipt.directory);
        Path path = receipt.directory.resolve(name);
        try (var channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            receipt.files.put(name, attributes(path));
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    public byte[] read(String id, String attempt, String name, ArtifactFile expected) throws IOException {
        if (!name.equals("sequences.spmf") && !name.equals("mapping.json")) throw new IOException("Unknown conversion artifact");
        if (expected == null || !reference(id, attempt, name).equals(expected.reference())
                || expected.sha256() == null || !expected.sha256().matches("[0-9a-f]{64}")
                || expected.sizeBytes() <= 0 || expected.sizeBytes() > maxBytes) throw new IOException("Invalid artifact descriptor");
        Path directory = attemptPath(id, attempt); check(directory);
        Path path = directory.resolve(name);
        var attrs = attributes(path);
        if (!attrs.isRegularFile() || attrs.isSymbolicLink() || attrs.isOther() || attrs.size() != expected.sizeBytes()) throw new IOException("Artifact shape mismatch");
        var bytes = new ByteArrayOutputStream((int) Math.min(65536, expected.sizeBytes()));
        try (var channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            var buffer = ByteBuffer.allocate(65536); long count = 0; int read;
            while ((read = channel.read(buffer)) != -1) {
                if (read == 0) continue;
                if (read > expected.sizeBytes() - count) throw new IOException("Artifact size changed");
                bytes.write(buffer.array(), 0, read); count += read; buffer.clear();
            }
            if (count != expected.sizeBytes()) throw new IOException("Artifact size changed");
        }
        var result = bytes.toByteArray();
        if (!ConversionArtifactCodec.sha256(result).equals(expected.sha256())) throw new IOException("Artifact checksum mismatch");
        return result;
    }

    public void discard(Receipt receipt) {
        if (receipt.owner != this) throw new IllegalArgumentException("Foreign conversion storage receipt");
        try {
            check(receipt.directory.getParent());
            if (!same(receipt.directory, receipt.identity)) throw new IOException("Attempt identity changed");
            for (var file : receipt.files.entrySet()) {
                Path path = receipt.directory.resolve(file.getKey());
                if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                    if (!same(path, file.getValue())) throw new IOException("Artifact identity changed");
                    Files.delete(path);
                }
            }
            Files.delete(receipt.directory); // unexpected entries prevent removal
        } catch (IOException failure) {
            log.warn("Conversion artifact retained for reconciliation: snapshotId={}, attemptId={}, failureType={}",
                    receipt.id, receipt.attempt, failure.getClass().getSimpleName());
        }
    }

    private Path attemptPath(String id, String attempt) throws IOException {
        try {
            if (!UUID.fromString(id).toString().equals(id) || !UUID.fromString(attempt).toString().equals(attempt)) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) { throw new IOException("Invalid conversion storage identity"); }
        return root.resolve(id).resolve("conversions").resolve(attempt);
    }
    public static String reference(String id, String attempt, String name) { return id + "/conversions/" + attempt + "/" + name; }
    private ArtifactFile descriptor(String id, String attempt, String name, byte[] bytes) {
        return new ArtifactFile(reference(id, attempt, name), ConversionArtifactCodec.sha256(bytes), bytes.length);
    }
    private static BasicFileAttributes attributes(Path path) throws IOException { return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
    private static void check(Path directory) throws IOException {
        for (Path path = directory; path != null; path = path.getParent()) {
            var attrs = attributes(path);
            if (!attrs.isDirectory() || attrs.isSymbolicLink() || attrs.isOther()) throw new IOException("Unsafe conversion storage ancestor");
        }
    }
    private static boolean same(Path path, BasicFileAttributes original) throws IOException {
        var current = attributes(path);
        return !current.isSymbolicLink() && !current.isOther() && current.isDirectory() == original.isDirectory()
                && current.isRegularFile() == original.isRegularFile()
                && (original.fileKey() != null ? Objects.equals(original.fileKey(), current.fileKey()) : original.creationTime().equals(current.creationTime()));
    }
    public static final class Receipt {
        private final ConversionArtifactStorage owner; private final String id, attempt; private final Path directory;
        private final BasicFileAttributes identity; private final Map<String, BasicFileAttributes> files = new LinkedHashMap<>();
        private ArtifactFile sequence, mapping;
        private Receipt(ConversionArtifactStorage owner, String id, String attempt, Path directory, BasicFileAttributes identity) {
            this.owner = owner; this.id = id; this.attempt = attempt; this.directory = directory; this.identity = identity;
        }
        public ArtifactFile sequence() { return sequence; }
        public ArtifactFile mapping() { return mapping; }
        @Override public String toString() { return "ConversionArtifactReceipt[snapshotId=" + id + "]"; }
    }
}
