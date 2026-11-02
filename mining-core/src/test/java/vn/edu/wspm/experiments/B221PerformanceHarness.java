package vn.edu.wspm.experiments;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import vn.edu.wspm.io.SequenceDatabaseFormat;

/** Manual B22.1 harness; deliberately not a correctness test or automatic benchmark assertion. */
public final class B221PerformanceHarness {

    private B221PerformanceHarness() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 5 && arguments.length != 6) {
            throw new IllegalArgumentException(
                    "usage: dataset format relativeMinSup warmups repetitions [retentionPolicy]");
        }
        var retention = (arguments.length == 6)
                ? vn.edu.wspm.mining.ResultRetentionPolicy.valueOf(arguments[5])
                : vn.edu.wspm.mining.ResultRetentionPolicy.SUPPORT_ONLY;
        var configuration = new MainWspmPreTree.RunConfiguration(
                Path.of(arguments[0]), SequenceDatabaseFormat.valueOf(arguments[1]), true,
                Double.parseDouble(arguments[2]), retention, false, false, false,
                Path.of("target/b22.1/unused.txt"));
        int warmups = Integer.parseInt(arguments[3]);
        int repetitions = Integer.parseInt(arguments[4]);

        try (var sink = new PrintStream(OutputStream.nullOutputStream())) {
            for (int run = -warmups; run < repetitions; run++) {
                System.gc(); // outside every measured execution scope
                Thread.sleep(200L);
                long gcCountBefore = gcCount();
                long gcMillisBefore = gcMillis();
                var measured = MainWspmPreTree.execute(configuration, sink);
                if (run >= 0) {
                    var metrics = measured.metrics();
                    var algorithm = metrics.algorithm();
                    var statistics = measured.miningResult().statistics();
                    System.out.printf(java.util.Locale.ROOT,
                            "run=%d prep=%.3f dfs=%.3f algorithm=%.3f total=%.3f "
                                    + "peakAlgorithm=%.3f peakTotal=%.3f gcCount=%d gcMillis=%d "
                                    + "candidates=%d patterns=%d%n",
                            run + 1,
                            algorithm.preprocessing().elapsedNanos() / 1_000_000.0,
                            algorithm.dfsMining().elapsedNanos() / 1_000_000.0,
                            algorithm.algorithmNanos() / 1_000_000.0,
                            metrics.totalExecutionNanos() / 1_000_000.0,
                            algorithm.observedPeakHeapAlgorithmBytes() / 1_000_000.0,
                            metrics.observedPeakHeapTotalBytes() / 1_000_000.0,
                            gcCount() - gcCountBefore, gcMillis() - gcMillisBefore,
                            statistics.candidateCount(), statistics.patternCount());
                }
                measured = null;
            }
        }
    }

    private static long gcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(bean -> Math.max(0L, bean.getCollectionCount())).sum();
    }

    private static long gcMillis() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(bean -> Math.max(0L, bean.getCollectionTime())).sum();
    }
}
