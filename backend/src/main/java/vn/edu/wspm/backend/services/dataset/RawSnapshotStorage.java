package vn.edu.wspm.backend.services.dataset;

import jakarta.servlet.MultipartConfigElement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Private, append-only acquisition storage. Client filenames never enter this class. */
@Slf4j
@Component
public class RawSnapshotStorage {
    private final Path root;
    private final long maxBytes;

    public RawSnapshotStorage(@Value("${wdm.snapshot-storage.root}") String root,
                              MultipartConfigElement multipart) {
        this.root = Path.of(root).normalize();
        if (!this.root.isAbsolute() || multipart.getMaxFileSize() <= 0) {
            throw new IllegalArgumentException("Snapshot storage requires an absolute root and a positive file limit");
        }
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path project = working.getFileName() != null && working.getFileName().toString().equals("backend")
                ? working.getParent() : working;
        if (this.root.startsWith(project)) {
            throw new IllegalArgumentException("Snapshot storage must be outside the application working tree");
        }
        this.maxBytes = multipart.getMaxFileSize();
    }

    public StoredRaw save(InputStream input) throws IOException {
        privateRoot();
        UUID id = UUID.randomUUID();
        Path directory = root.resolve(id.toString());
        // Exclusive directory allocation is the no-overwrite boundary, including concurrent uploads.
        Files.createDirectory(directory);
        var allocation = new Allocation(id.toString(), directory);
        try {
            allocation.directoryIdentity = attributes(directory);
            MessageDigest digest = sha256();
            long size = 0;
            try (FileChannel output = FileChannel.open(allocation.file,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                allocation.fileIdentity = attributes(allocation.file);
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (read == 0) continue;
                    if (read > maxBytes - size) {
                        throw new ApplicationException(ErrorCode.PAYLOAD_TOO_LARGE,
                                "Upload exceeds configured file or request limits.");
                    }
                    digest.update(buffer, 0, read);
                    ByteBuffer bytes = ByteBuffer.wrap(buffer, 0, read);
                    while (bytes.hasRemaining()) output.write(bytes);
                    size += read;
                }
                if (size == 0) {
                    throw ApplicationException.invalid("file", "EMPTY_FILE", "Supply a nonempty CSV file.");
                }
                output.force(true);
            }
            return new StoredRaw(this, allocation, HexFormat.of().formatHex(digest.digest()), size);
        } catch (IOException | RuntimeException failure) {
            discard(allocation);
            throw failure;
        }
    }

    /** Availability is an existence/shape/size probe, not a checksum attestation. Never creates files. */
    public String availability(String id, String reference, String hash, String size) {
        if (reference == null) return "MISSING";
        try {
            long expected = expectedSize(hash, size);
            Path file = readPath(id, reference);
            var attrs = attributes(file);
            return attrs.isRegularFile() && !attrs.isSymbolicLink() && !attrs.isOther()
                    && attrs.size() == expected ? "AVAILABLE" : "UNAVAILABLE";
        } catch (NoSuchFileException missing) {
            return "MISSING";
        } catch (IOException | IllegalArgumentException unavailable) {
            return "UNAVAILABLE";
        }
    }

    /** Buffer only within the configured bound so the exact verified bytes are what HTTP sends. */
    public byte[] verifiedBytes(String id, String reference, String hash, String size) throws IOException {
        long expected = expectedSize(hash, size);
        Path file = readPath(id, reference);
        var attrs = attributes(file);
        if (!attrs.isRegularFile() || attrs.isSymbolicLink() || attrs.isOther() || attrs.size() != expected) {
            throw new IOException("Raw artifact metadata mismatch");
        }
        MessageDigest digest = sha256();
        var bytes = new ByteArrayOutputStream((int) Math.min(expected, 64 * 1024));
        try (var input = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            if (input.size() != expected) throw new IOException("Raw artifact changed");
            ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
            long count = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) continue;
                if (read > expected - count) throw new IOException("Raw artifact exceeded expected size");
                digest.update(buffer.array(), 0, read);
                bytes.write(buffer.array(), 0, read);
                count += read;
                buffer.clear();
            }
            if (count != expected || !HexFormat.of().formatHex(digest.digest()).equals(hash)) {
                throw new IOException("Raw artifact checksum mismatch");
            }
        }
        return bytes.toByteArray();
    }

    private long expectedSize(String hash, String size) throws IOException {
        if (hash == null || !hash.matches("[0-9a-f]{64}") || size == null || !size.matches("[1-9][0-9]*")) {
            throw new IOException("Missing or invalid acquisition metadata");
        }
        try {
            long count = Long.parseLong(size);
            if (count > maxBytes || count > Integer.MAX_VALUE - 8L) throw new IOException("Raw artifact exceeds read limit");
            return count;
        } catch (NumberFormatException invalid) {
            throw new IOException("Invalid raw size");
        }
    }

    private Path readPath(String id, String reference) throws IOException {
        if (id == null || !id.equals(UUID.fromString(id).toString()) || !(id + "/raw.csv").equals(reference)) {
            throw new IOException("Invalid raw artifact reference");
        }
        Path directory = root.resolve(id);
        checkAncestors(directory);
        return directory.resolve("raw.csv");
    }

    /** Only accepts a receipt minted by this storage instance; never accepts a public path or ID. */
    void discard(StoredRaw raw) {
        if (raw.owner != this) throw new IllegalArgumentException("Foreign storage receipt");
        discard(raw.allocation);
    }

    private void discard(Allocation allocation) {
        try {
            checkAncestors(root);
            if (!allocation.directory.getParent().equals(root)
                    || !sameIdentity(allocation.directory, allocation.directoryIdentity)) {
                log.warn("Snapshot artifact retained for reconciliation: snapshotId={}", allocation.id);
                return;
            }
            if (allocation.fileIdentity != null && sameIdentity(allocation.file, allocation.fileIdentity)) {
                Files.delete(allocation.file);
            }
            // Never enumerate or recursively delete; an unexpected entry prevents directory removal.
            Files.delete(allocation.directory);
        } catch (IOException failure) {
            log.warn("Snapshot artifact cleanup incomplete: snapshotId={}, failureType={}",
                    allocation.id, failure.getClass().getSimpleName());
        }
    }

    private void privateRoot() throws IOException {
        checkAncestors(root);
        Files.createDirectories(root);
        checkAncestors(root);
        if (!attributes(root).isDirectory()) throw new IOException("Invalid snapshot storage root");
    }

    private static void checkAncestors(Path path) throws IOException {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                var attributes = attributes(current);
                if (attributes.isSymbolicLink() || attributes.isOther() || !attributes.isDirectory()) {
                    throw new IOException("Snapshot storage may not traverse links or non-directories");
                }
            }
        }
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean sameIdentity(Path path, BasicFileAttributes original) throws IOException {
        var attributes = attributes(path);
        if (original == null || attributes.isSymbolicLink() || attributes.isOther()
                || original.isDirectory() != attributes.isDirectory()
                || original.isRegularFile() != attributes.isRegularFile()) return false;
        // Windows' provider can return null fileKey. The private receipt, exclusive UUID
        // directory and creation identity are then the ownership checks under a trusted OS root.
        return original.fileKey() != null ? Objects.equals(original.fileKey(), attributes.fileKey())
                : original.creationTime().equals(attributes.creationTime());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private static final class Allocation {
        final String id;
        final Path directory;
        final Path file;
        BasicFileAttributes directoryIdentity;
        BasicFileAttributes fileIdentity;

        Allocation(String id, Path directory) {
            this.id = id;
            this.directory = directory;
            this.file = directory.resolve("raw.csv");
        }
    }

    public static final class StoredRaw {
        private final RawSnapshotStorage owner;
        private final Allocation allocation;
        private final String sha256;
        private final long sizeBytes;

        private StoredRaw(RawSnapshotStorage owner, Allocation allocation, String sha256, long sizeBytes) {
            this.owner = owner;
            this.allocation = allocation;
            this.sha256 = sha256;
            this.sizeBytes = sizeBytes;
        }

        public String snapshotId() { return allocation.id; }
        public String reference() { return allocation.id + "/raw.csv"; }
        public String sha256() { return sha256; }
        public long sizeBytes() { return sizeBytes; }

        @Override public String toString() { return "StoredRaw[snapshotId=" + snapshotId() + "]"; }
    }
}
