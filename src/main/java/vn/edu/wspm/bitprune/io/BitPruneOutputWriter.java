package vn.edu.wspm.bitprune.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import vn.edu.wspm.bitprune.result.BitPruneOutputProjection;

/**
 * Stable physical file writer for {@link BitPruneOutputProjection} (C14.2).
 *
 * <p>Writes the immutable projection content to disk with:
 * <ul>
 *   <li>Exact UTF-8 encoding (no BOM);</li>
 *   <li>Exact LF newlines as preserved in {@link BitPruneOutputProjection#content()};</li>
 *   <li>Recursive parent directory creation when missing;</li>
 *   <li>Create or truncate file replacement (never append);</li>
 *   <li>Zero-byte regular file creation when content is empty;</li>
 *   <li>Unwrapped {@link IOException} propagation;</li>
 *   <li>Zero timer, sampler, runner, or console logic.</li>
 * </ul>
 * </p>
 *
 * <p>This class is stateless and does not retain any references or file handles.</p>
 */
public final class BitPruneOutputWriter {

    /**
     * Constructs a stateless output writer.
     */
    public BitPruneOutputWriter() {
    }

    /**
     * Writes the given projection to the specified output path.
     *
     * @param outputPath destination path, must not be null
     * @param projection output projection to write, must not be null
     * @throws NullPointerException if outputPath or projection is null
     * @throws IOException if an I/O error occurs writing to the file
     */
    public void write(Path outputPath, BitPruneOutputProjection projection) throws IOException {
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        Objects.requireNonNull(projection, "projection must not be null");

        Path absoluteOutput = outputPath.toAbsolutePath().normalize();
        Path parent = absoluteOutput.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(
                absoluteOutput,
                projection.content(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }
}
