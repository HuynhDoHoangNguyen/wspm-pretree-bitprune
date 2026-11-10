package vn.edu.wspm.e2e;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission;

/** Non-destructive, same-JVM observer of the original result. Never mines/acks or adds an HTTP route. */
public final class EngineRunEvidenceObserver implements AutoCloseable {
    private final Path directory;
    private final ManagedRunExecutionAdmission manager;
    private final JdbcTemplate jdbc;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    public EngineRunEvidenceObserver(Path directory, ManagedRunExecutionAdmission manager, JdbcTemplate jdbc) {
        this(directory, manager, jdbc, true);
    }
    EngineRunEvidenceObserver(Path directory, ManagedRunExecutionAdmission manager, JdbcTemplate jdbc, boolean schedule) {
        this.directory = directory.toAbsolutePath().normalize(); this.manager = manager; this.jdbc = jdbc;
        if (schedule) worker.scheduleWithFixedDelay(this::observe, 50, 50, TimeUnit.MILLISECONDS);
    }
    static List<String> ids(String input) {
        if (input.length() > 1024) throw new IllegalArgumentException("Inbox too large");
        var ids = input.lines().filter(s -> !s.isBlank()).toList();
        if (ids.size() > 2 || new HashSet<>(ids).size() != ids.size()) throw new IllegalArgumentException("Two distinct runs maximum");
        for (var id : ids) if (!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new IllegalArgumentException("Canonical run ID required");
        return ids;
    }
    void observe() {
        try {
            var inbox = directory.resolve("INBOX"); if (!Files.exists(inbox)) return;
            if (Files.size(inbox) > 1024) throw new IllegalArgumentException("Inbox too large");
            for (var id : ids(Files.readString(inbox))) {
                if (Files.exists(directory.resolve(id + ".properties"))) continue;
                var found = manager.outcome(id); if (found.isEmpty() || !found.get().lifecycleConfirmed()) continue;
                write(id, found.get());
            }
        } catch (Exception failure) {
            try { Files.writeString(directory.resolve("OBSERVER-ERROR"), failure.getClass().getSimpleName()); }
            catch (IOException ignored) { /* Runner times out if even the error marker cannot be published. */ }
        }
    }
    private void write(String id, ManagedRunExecutionAdmission.Outcome outcome) throws Exception {
        var rows = jdbc.queryForList("SELECT r.*,s.sequence_sha256,s.mapping_sha256,s.raw_sha256 FROM MINING_RUN r "
                + "JOIN DATASET_SNAPSHOT s ON s.snapshot_id=r.snapshot_id WHERE r.run_id=?", id);
        if (rows.size() != 1) throw new IllegalStateException("Observed run missing");
        var row = rows.getFirst(); var p = new Properties();
        for (String key : List.of("run_id", "snapshot_id", "algorithm", "status", "minsup_mode", "minsup_configured", "effective_threshold",
                "mean_w", "max_w", "min_w", "pattern_count", "candidate_count", "dataset_io_ms", "preprocessing_ms", "dfs_mining_ms",
                "complete_algorithm_ms", "output_io_ms", "total_execution_ms", "peak_sampled_jvm_heap_bytes", "execution_owner_id",
                "execution_attempt_id", "sequence_sha256", "mapping_sha256", "raw_sha256")) p.setProperty("db." + key, Objects.toString(row.get(key), "null"));
        if (!id.equals(p.getProperty("db.run_id")) || row.get("execution_attempt_id") == null || row.get("execution_owner_id") == null)
            throw new IllegalStateException("Owned run binding required");
        p.setProperty("lifecycleConfirmed", "true"); p.setProperty("retainedCount", Integer.toString(manager.retainedCount()));
        p.setProperty("hasResult", Boolean.toString(outcome.result() != null));
        p.setProperty("failureCode", Objects.toString(outcome.failureCode(), "null"));
        if (outcome.result() != null) {
            var result = outcome.result(); var capture = Objects.requireNonNull(outcome.metrics()); var resolved = result.minimumSupport();
            if (!"SUCCEEDED".equals(row.get("status")) || !result.algorithm().name().equals(row.get("algorithm"))) throw new IllegalStateException("Result binding mismatch");
            p.setProperty("configured.bits", bits(resolved.configuredValue())); p.setProperty("resolved.bits", bits(resolved.effectiveThreshold()));
            p.setProperty("relative.bits", resolved.relativeMinimumSupport().isPresent() ? bits(resolved.relativeMinimumSupport().getAsDouble()) : "null");
            p.setProperty("mean.bits", bits(result.globalMeanWeight()));
            p.setProperty("sequenceCount", Integer.toString(resolved.numberOfSequences()));
            p.setProperty("map.count", Long.toString(result.patternCount()));
            p.setProperty("native.candidates", Long.toString(result.statistics().candidateCount()));
            p.setProperty("native.preprocessing", Long.toString(result.metrics().preprocessing().elapsedNanos()));
            p.setProperty("native.dfs", Long.toString(result.metrics().dfsMining().elapsedNanos()));
            p.setProperty("native.complete", Long.toString(result.metrics().completeAlgorithmNanos()));
            p.setProperty("native.heap", Long.toString(result.metrics().observedPeakHeapAlgorithmBytes()));
            p.setProperty("capture.dataset", Long.toString(capture.datasetIoNanos())); p.setProperty("capture.total", Long.toString(capture.totalExecutionNanos()));
            if (result.supports().size() > 100000) throw new IllegalStateException("Tiny fixture evidence bound exceeded");
            var map = new TreeMap<String, Integer>();
            result.supports().forEach((pattern, support) -> { if (map.put(pattern.serialize(), support) != null) throw new IllegalStateException("Duplicate canonical identity"); });
            if (map.size() != result.patternCount()) throw new IllegalStateException("Truncated map");
            var text = new StringBuilder(); map.forEach((pattern, support) -> text.append(pattern).append('\t').append(support).append('\n'));
            byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
            p.setProperty("map.sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            atomic(directory.resolve(id + ".map.tsv"), bytes);
        } else if (!"FAILED".equals(row.get("status"))) throw new IllegalStateException("Failure binding mismatch");
        var bytes = new ByteArrayOutputStream(); p.store(bytes, "Test-only original retained result; no acknowledgement");
        atomic(directory.resolve(id + ".properties"), bytes.toByteArray()); // Completion marker last.
    }
    static String bits(double value) { return Long.toUnsignedString(Double.doubleToRawLongBits(value), 16); }
    static void atomic(Path file, byte[] bytes) throws IOException {
        var temp = file.resolveSibling(file.getFileName() + ".tmp"); Files.write(temp, bytes);
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    @Override public void close() { worker.shutdownNow(); }
}
