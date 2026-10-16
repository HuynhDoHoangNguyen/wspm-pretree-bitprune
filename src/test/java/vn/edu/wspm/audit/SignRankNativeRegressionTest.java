package vn.edu.wspm.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.mining.WspmPreTreeMiner;

/**
 * B22.1 reference captured with the pre-change dense Prime-product implementation.
 * Canonical SHA-256 inputs contain every pattern/support row, every sequence/position rank,
 * and every tree edge; this therefore checks exact content rather than aggregate counts.
 */
final class SignRankNativeRegressionTest {

    private static final Path SIGN = Path.of("src/main/resources/datasets/SIGN.txt");

    @Test
    void signPointThreeMatchesCompletePreChangeResult() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(SIGN),
                "B22.1 requires the local ignored SIGN research input: " + SIGN);
        var database = new SpmfSequenceParser().parse(SIGN);
        var result = new WspmPreTreeMiner().mine(database, MinimumSupport.percentage(0.3));
        var patterns = result.acceptedPatterns().stream()
                .sorted(Comparator.comparing(pattern -> pattern.serialize())).toList();
        var map = new StringBuilder();
        var encodings = new StringBuilder();
        var tree = new StringBuilder();

        for (var pattern : patterns) {
            String key = pattern.serialize();
            var encoding = result.encoding(pattern);
            map.append(key).append('\t').append(result.support(pattern)).append('\n');
            encodings.append(key).append('|');
            for (int block = 0; block < encoding.sequenceBlock().blockCount(); block++) {
                encodings.append(encoding.sequenceBlock().blockRank(block)).append(',');
            }
            encodings.append('|');
            for (var positions : encoding.positionBlocks()) {
                encodings.append(positions.sid()).append(':');
                for (int block = 0; block < positions.blockCount(); block++) {
                    encodings.append(positions.blockRank(block)).append(',');
                }
                encodings.append(';');
            }
            encodings.append('\n');
            var node = result.prefixTree().find(pattern).orElseThrow();
            tree.append(key).append('|')
                    .append(node.parent().orElseThrow().pattern().serialize()).append('|')
                    .append(node.extensionType().orElseThrow()).append('\n');
        }

        var statistics = result.statistics();
        String counters = statistics.sequenceCount() + "|" + statistics.f1Count() + "|"
                + statistics.candidateCount() + "|" + statistics.acceptedCandidateCount() + "|"
                + statistics.rejectedCandidateCount() + "|" + statistics.iCandidateCount() + "|"
                + statistics.sCandidateCount() + "|" + statistics.expandedNodeCount() + "|"
                + statistics.patternCount();

        assertEquals(1_166, patterns.size());
        assertEquals("98094C7460AEE9E17EC4FE31CE6E7858E1FAF6B9F2C2910391C065A668795D7D",
                sha256(map));
        assertEquals("7B3959556B81BEF69D1EC6615503D78CEFD6F7CBBC12077B22A85C5AC890F36E",
                sha256(encodings));
        assertEquals("0EA6A64326406F3C571BF4C7D5093A12B903886DD2DE476D92F7EC7DE9F2A59F",
                sha256(tree));
        assertEquals("730|69|92027|1097|90930|11573|80454|1166|1166", counters);
    }

    private static String sha256(CharSequence value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.toString().getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().withUpperCase().formatHex(digest);
    }
}
