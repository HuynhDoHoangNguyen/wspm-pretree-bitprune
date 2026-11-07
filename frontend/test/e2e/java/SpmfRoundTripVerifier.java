import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import vn.edu.wspm.io.SpmfSequenceParser;

/** Test-only Java 21 source launcher. Expected grammar: comma-separated items;
 * semicolon-separated itemsets; one sequence per line. This is not an SPMF parser. */
public final class SpmfRoundTripVerifier {
    public static void main(String[] args) {
        try {
            if (args.length != 2) throw new IllegalArgumentException();
            var expected = new ArrayList<List<List<Integer>>>();
            for (String line : Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8)) {
                var itemsets = new ArrayList<List<Integer>>();
                for (String group : line.split(";", -1)) {
                    var items = new ArrayList<Integer>();
                    int previous = 0;
                    for (String token : group.split(",", -1)) {
                        if (!token.matches("[1-9][0-9]*")) throw new IllegalArgumentException();
                        int item = Integer.parseInt(token);
                        if (item <= previous) throw new IllegalArgumentException();
                        items.add(item); previous = item;
                    }
                    itemsets.add(List.copyOf(items));
                }
                expected.add(List.copyOf(itemsets));
            }
            if (expected.isEmpty()) throw new IllegalArgumentException();
            var actual = new SpmfSequenceParser().parse(Path.of(args[0]));
            if (actual.size() != expected.size()) throw new AssertionError();
            for (int sid = 0; sid < expected.size(); sid++) {
                var record = actual.records().get(sid);
                var itemsets = record.sequence().itemsets();
                if (record.sid() != sid + 1 || itemsets.size() != expected.get(sid).size()) throw new AssertionError();
                for (int position = 0; position < itemsets.size(); position++) {
                    var items = itemsets.get(position).items().stream().map(item -> item.id()).toList();
                    if (!items.equals(expected.get(sid).get(position))) throw new AssertionError();
                }
            }
            System.out.println("PASS mining-core browser round-trip: sequences=" + expected.size());
        } catch (Exception | AssertionError failure) {
            // Never echo input bytes, private paths or untrusted parser messages.
            System.err.println("FAIL mining-core browser round-trip verification.");
            System.exit(1);
        }
    }
}
