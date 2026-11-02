package vn.edu.wspm.experiments;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import vn.edu.wspm.io.SequenceDatabaseFormat;
import vn.edu.wspm.mining.WspmMiningResult;

/** Manual post-GC retained-object diagnostic for B22.2; not executed by Maven tests. */
public final class B222RetentionHarness {

    private static volatile WspmMiningResult retainedResult;

    private B222RetentionHarness() {}

    public static void main(String[] arguments) throws Exception {
        var configuration = new MainWspmPreTree.RunConfiguration(
                Path.of(arguments[0]), SequenceDatabaseFormat.valueOf(arguments[1]), true,
                Double.parseDouble(arguments[2]), vn.edu.wspm.mining.ResultRetentionPolicy.FULL_ENCODING,
                false, false, false,
                Path.of("target/b22.2/unused.txt"));
        try (var sink = new PrintStream(OutputStream.nullOutputStream())) {
            retainedResult = MainWspmPreTree.execute(configuration, sink).miningResult();
        }

        long supportingSidEntries = 0;
        long positionRankEntries = 0;
        var coordinateSystems = new IdentityHashMap<Object, Boolean>();
        for (var encoding : retainedResult.encodings().values()) {
            supportingSidEntries = Math.addExact(
                    supportingSidEntries, encoding.compactSupportingSidCount());
            positionRankEntries = Math.addExact(
                    positionRankEntries, encoding.compactPositionRankCount());
            coordinateSystems.put(encoding.coordinates(), Boolean.TRUE);
        }
        System.out.printf(
                "pid=%d patterns=%d candidates=%d compactSidEntries=%d positionRanks=%d "
                        + "sharedCoordinateSystems=%d%n",
                ProcessHandle.current().pid(), retainedResult.acceptedPatterns().size(),
                retainedResult.statistics().candidateCount(), supportingSidEntries,
                positionRankEntries, coordinateSystems.size());
        System.out.flush();
        System.in.read();
        long heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        System.out.printf("postHistogramHeapBytes=%d%n", heap);
    }
}
