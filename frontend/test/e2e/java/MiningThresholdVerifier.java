import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.zip.ZipFile;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportMode;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.io.SpmfSequenceParser;

/** Test-only Java 21 source launcher: actual downloaded SPMF plus captured wire TSV.
 * Columns: mode, sent value, response count, response configured value,
 * response fraction (null for ABSOLUTE), response threshold, HTTP status.
 * Rejected cases use '-' for unavailable response numeric fields. */
public final class MiningThresholdVerifier {
    private static void same(double expected, String actual) {
        if (Double.doubleToRawLongBits(expected) != Double.doubleToRawLongBits(Double.parseDouble(actual))) {
            throw new AssertionError();
        }
    }
    public static void main(String[] args) {
        try {
            if (args.length != 5) throw new IllegalArgumentException();
            // The verifier must use the exact core jar embedded in the running backend package.
            try (var backend = new ZipFile(args[3])) {
                var entry = backend.getEntry("BOOT-INF/lib/mining-core-1.0-SNAPSHOT.jar");
                if (entry == null) throw new AssertionError();
                try (var input = backend.getInputStream(entry)) {
                    var sha = MessageDigest.getInstance("SHA-256");
                    if (!MessageDigest.isEqual(sha.digest(input.readAllBytes()), sha.digest(Files.readAllBytes(Path.of(args[4]))))) throw new AssertionError();
                }
            }
            int count = new SpmfSequenceParser().parse(Path.of(args[0])).size();
            if (count != Integer.parseInt(args[2]) || count <= 0) throw new AssertionError();
            var lines = Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8);
            if (lines.isEmpty()) throw new AssertionError();
            for (String line : lines) {
                var fields = line.split("\t", -1);
                if (fields.length != 7 || Integer.parseInt(fields[2]) != count) throw new AssertionError();
                var resolved = new MinimumSupportResolver().resolve(new MinimumSupport(
                        Double.parseDouble(fields[1]), MinimumSupportMode.valueOf(fields[0])), count);
                int status = Integer.parseInt(fields[6]);
                if (status == 200) {
                    same(resolved.configuredValue(), fields[3]);
                    if (resolved.relativeMinimumSupport().isPresent()) same(resolved.relativeMinimumSupport().orElseThrow(), fields[4]);
                    else if (!fields[4].equals("null")) throw new AssertionError();
                    same(resolved.effectiveThreshold(), fields[5]);
                } else if (status != 422 || resolved.effectiveThreshold() != 0.0) throw new AssertionError();
            }
            System.out.println("PASS core threshold verification: cases=" + lines.size() + ", sequences=" + count);
        } catch (Exception | AssertionError invalid) {
            System.err.println("FAIL core threshold verification.");
            System.exit(1);
        }
    }
}
