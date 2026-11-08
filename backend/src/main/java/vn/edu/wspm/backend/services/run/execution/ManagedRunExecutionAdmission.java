package vn.edu.wspm.backend.services.run.execution;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.services.run.PreparedRun;
import vn.edu.wspm.backend.services.run.RunExecutionAdmission;
import vn.edu.wspm.backend.services.run.engine.MiningEngine;
import vn.edu.wspm.backend.services.run.engine.MiningEngineResult;
import vn.edu.wspm.backend.services.run.persistence.RunPersistenceContext;

/** Opt-in bounded executor AND completion store. Outcomes stay in memory until explicit handoff acknowledgement. */
@Slf4j
public final class ManagedRunExecutionAdmission implements RunExecutionAdmission, AutoCloseable {
    /** Internal task state only; not MINING_RUN.status. */
    public enum State { RESERVED, ACTIVATING, SUBMITTED, RUNNING, COMPLETED, FAILED, CLOSED, ACKNOWLEDGED }
    public record Outcome(String runId, UUID token, State state, MiningEngineResult result, String failureCode,
            boolean lifecycleConfirmed, RunMetricsCapture metrics, RunPersistenceContext persistenceContext) {
        public Outcome {
            Objects.requireNonNull(runId); Objects.requireNonNull(token);
            if (result == null && metrics != null) throw new IllegalArgumentException("No complete metrics for failure");
            if (result != null && persistenceContext == null
                    || persistenceContext != null && !runId.equals(persistenceContext.runId())) {
                throw new IllegalArgumentException("Run-bound persistence context required for success");
            }
            if (state == State.COMPLETED ? result == null || failureCode != null
                    : state != State.FAILED || result != null || failureCode == null) {
                throw new IllegalArgumentException("Only complete success or safe failure outcomes are allowed");
            }
        }
        @Override public String toString() { return "ExecutionOutcome[runId=" + runId + ", state=" + state + "]"; }
    }
    private static final AtomicLong POOLS = new AtomicLong();
    private final Object lock = new Object();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final ExecutionLimits limits;
    private final MiningEngine engine;
    private final RunExecutionVerifier verifier;
    private final ThreadPoolExecutor executor;
    private final RunLifecycleService lifecycle;
    private final ScheduledExecutorService maintenance;
    private final java.util.function.LongSupplier ticker;
    private boolean closing;

    public ManagedRunExecutionAdmission(ExecutionLimits limits, MiningEngine engine, RunExecutionVerifier verifier, RunLifecycleService lifecycle) {
        this(limits, engine, verifier, newExecutor(limits), Objects.requireNonNull(lifecycle));
    }
    /** Isolated scheduler tests only; production configuration always supplies real fenced lifecycle. */
    ManagedRunExecutionAdmission(ExecutionLimits limits, MiningEngine engine, RunExecutionVerifier verifier) {
        this(limits, engine, verifier, newExecutor(limits), null);
    }

    /** Package-local seam for deterministic rejection/resource tests; production always uses the bounded factory. */
    ManagedRunExecutionAdmission(ExecutionLimits limits, MiningEngine engine, RunExecutionVerifier verifier, ThreadPoolExecutor executor) {
        this(limits, engine, verifier, executor, null);
    }
    ManagedRunExecutionAdmission(ExecutionLimits limits, MiningEngine engine, RunExecutionVerifier verifier,
            ThreadPoolExecutor executor, RunLifecycleService lifecycle) {
        this(limits, engine, verifier, executor, lifecycle, System::nanoTime);
    }
    ManagedRunExecutionAdmission(ExecutionLimits limits, MiningEngine engine, RunExecutionVerifier verifier,
            ThreadPoolExecutor executor, RunLifecycleService lifecycle, java.util.function.LongSupplier ticker) {
        this.limits = Objects.requireNonNull(limits); this.engine = Objects.requireNonNull(engine);
        this.verifier = Objects.requireNonNull(verifier); this.executor = Objects.requireNonNull(executor);
        this.lifecycle = lifecycle;
        this.ticker = Objects.requireNonNull(ticker);
        maintenance = lifecycle == null ? null : Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task, "wspm-lifecycle-" + POOLS.incrementAndGet()); thread.setDaemon(false); return thread;
        });
        if (maintenance != null) maintenance.scheduleWithFixedDelay(this::maintainSafely,
                lifecycle.heartbeatMillis(), lifecycle.heartbeatMillis(), TimeUnit.MILLISECONDS);
    }

    private static ThreadPoolExecutor newExecutor(ExecutionLimits limits) {
        long pool = POOLS.incrementAndGet(); var workers = new AtomicLong();
        ThreadFactory threads = task -> {
            var thread = new Thread(task, "wspm-mining-" + pool + "-" + workers.incrementAndGet());
            thread.setDaemon(false);
            thread.setUncaughtExceptionHandler((t, error) -> log.error("Mining worker terminated: thread={}, type={}",
                    t.getName(), error.getClass().getSimpleName()));
            return thread;
        };
        return new ThreadPoolExecutor(limits.parallelism(), limits.parallelism(), 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(limits.queueCapacity()), threads, new ThreadPoolExecutor.AbortPolicy());
    }

    @Override public Reservation reserve(String runId, PreparedRun input) {
        Objects.requireNonNull(input);
        if (runId == null || runId.isBlank() || runId.length() > 40) throw new IllegalArgumentException("Invalid internal run ID");
        Entry entry;
        synchronized (lock) {
            if (closing || entries.size() >= limits.capacity() || entries.containsKey(runId)) throw unavailable();
            entry = new Entry(runId, input, lifecycle == null ? null : lifecycle.newOwnership()); entries.put(runId, entry);
        }
        log.debug("Execution capacity reserved: runId={}", runId);
        return new Reservation() {
            public Optional<ExecutionOwnership> ownership() { return Optional.ofNullable(entry.owner); }
            public void activate() { ManagedRunExecutionAdmission.this.activate(entry); }
            public void close() {
                synchronized (lock) { if (!entry.admitted) releaseUnadmitted(entry); }
            }
        };
    }

    private void activate(Entry entry) {
        PreparedRun input;
        synchronized (lock) {
            if (entry.admitted) return;
            if (closing || entry.state != State.RESERVED || entries.get(entry.id) != entry) throw unavailable();
            entry.state = State.ACTIVATING; input = entry.input;
        }
        try {
            // DB I/O outside registry lock. The worker cannot be submitted before this fresh commit check.
            if (lifecycle == null) verifier.requireCommitted(entry.id, input);
            else verifier.requireQueuedOwned(entry.id, input, entry.owner);
            synchronized (lock) {
                if (closing || entry.state != State.ACTIVATING || entries.get(entry.id) != entry) throw unavailable();
                entry.state = State.SUBMITTED;
                executor.execute(() -> run(entry)); // AbortPolicy, never HTTP-thread execution.
                entry.admitted = true; // Worker must acquire this same lock before starting.
            }
            log.info("Execution admitted: runId={}", entry.id);
        } catch (RuntimeException failure) {
            if (lifecycle != null && !entry.admitted) lifecycle.confirmFailure(entry.id, entry.owner, RunFailureCode.ADMISSION_FAILED);
            synchronized (lock) { if (!entry.admitted) releaseUnadmitted(entry); }
            if (failure instanceof ApplicationException expected) throw expected;
            if (failure instanceof RejectedExecutionException) throw unavailable();
            throw failure;
        }
    }

    private void run(Entry entry) {
        PreparedRun input;
        synchronized (lock) {
            if (!entry.admitted || entry.state != State.SUBMITTED) return;
            entry.state = State.RUNNING; input = entry.input;
        }
        log.info("Execution worker started: runId={}", entry.id);
        MiningEngineResult result = null; RuntimeException failure = null;
        RunMetricsCapture metrics = null;
        RunPersistenceContext persistenceContext = null;
        RunFailureCode code = RunFailureCode.CLAIM_NOT_CONFIRMED;
        try {
            if (lifecycle != null && !lifecycle.claim(entry.id, entry.owner)) throw unavailable();
            long start = ticker.getAsLong();
            code = RunFailureCode.INPUT_REVERIFICATION_FAILED;
            var measured = lifecycle == null ? null : Objects.requireNonNull(verifier.refreshMeasuredOwned(entry.id, input, entry.owner, ticker));
            var fresh = measured == null ? verifier.refresh(entry.id, input) : measured.input();
            persistenceContext = RunPersistenceContext.from(entry.id, fresh);
            code = RunFailureCode.ENGINE_EXECUTION_FAILED;
            result = Objects.requireNonNull(engine.execute(fresh.engineRequest()), "engine result is required");
            long total = ticker.getAsLong() - start;
            if (lifecycle != null) metrics = RunMetricsCapture.from(result, measured.datasetIoNanos(), total);
        } catch (RuntimeException caught) { failure = caught; }
        boolean confirmed = confirm(entry, result, metrics, code);
        CompletableFuture<Outcome> finished;
        var state = result == null ? State.FAILED : State.COMPLETED;
        var outcome = new Outcome(entry.id, entry.token, state, result,
                result != null ? null : lifecycle == null ? "EXECUTION_FAILED" : code.name(), confirmed, metrics, persistenceContext);
        synchronized (lock) {
            entry.outcome = outcome; finished = entry.finished; entry.failureCode = code;
            entry.input = null; entry.state = state;
        }
        finished.complete(outcome); // Consumer callbacks run outside the registry lock.
        if (failure != null) {
            if (failure instanceof ApplicationException expected) {
                log.warn("Execution rejected; outcome retained: runId={}, code={}", entry.id, expected.code());
            } else {
                var safe = new IllegalStateException("Execution failed: " + failure.getClass().getSimpleName());
                safe.setStackTrace(failure.getStackTrace()); log.error("Execution failed; outcome retained: runId={}", entry.id, safe);
            }
        } else log.info("Execution result retained: runId={}, patternCount={}, lifecycleConfirmed={}", entry.id, result.patternCount(), confirmed);
    }

    private boolean confirm(Entry entry, MiningEngineResult result, RunMetricsCapture metrics, RunFailureCode code) {
        if (lifecycle == null) return false;
        try { return result == null ? lifecycle.confirmFailure(entry.id, entry.owner, code)
                : metrics != null && lifecycle.confirmSuccess(entry.id, entry.owner, metrics); }
        catch (RuntimeException unavailable) { log.warn("Terminal confirmation pending: runId={}, type={}", entry.id, unavailable.getClass().getSimpleName()); return false; }
    }

    /** One bounded maintenance pass. Package access permits deterministic lease tests without sleeps. */
    void maintain() {
        if (lifecycle == null) return;
        List<Entry> snapshot;
        synchronized (lock) { snapshot = entries.values().stream().filter(e -> e.admitted).toList(); }
        for (var entry : snapshot) {
            Outcome current;
            synchronized (lock) { if (entries.get(entry.id) != entry) continue; current = entry.outcome; }
            if (current == null || !current.lifecycleConfirmed()) lifecycle.renew(entry.id, entry.owner);
            if (current != null && !current.lifecycleConfirmed() && confirm(entry, current.result(), current.metrics(), entry.failureCode)) {
                synchronized (lock) {
                    if (entries.get(entry.id) == entry && entry.outcome == current) entry.outcome = new Outcome(
                            current.runId(), current.token(), current.state(), current.result(), current.failureCode(), true, current.metrics(), current.persistenceContext());
                }
            }
        }
        lifecycle.recover();
    }
    private void maintainSafely() {
        try { maintain(); } catch (RuntimeException failure) { log.warn("Lifecycle maintenance incomplete: type={}", failure.getClass().getSimpleName()); }
    }

    /** Non-destructive internal handoff read. Not an HTTP endpoint or durable storage. */
    public Optional<Outcome> outcome(String runId) {
        synchronized (lock) { var entry = entries.get(runId); return entry == null ? Optional.empty() : Optional.ofNullable(entry.outcome); }
    }
    public Optional<State> state(String runId) {
        synchronized (lock) { var entry = entries.get(runId); return entry == null ? Optional.empty() : Optional.of(entry.state); }
    }
    /** Read-only completion signal for internal handoff; observing it never releases capacity. */
    public Optional<CompletionStage<Outcome>> completion(String runId) {
        synchronized (lock) {
            var entry = entries.get(runId);
            return entry == null || !entry.admitted ? Optional.empty() : Optional.of(entry.finished.minimalCompletionStage());
        }
    }
    public int retainedCount() { synchronized (lock) { return entries.size(); } }
    /** One coherent read-only view of this JVM. Never exposes ownership/ack tokens or result maps. */
    public record CapacityEntry(String runId, String state) {}
    public record CapacitySnapshot(int capacity, int used, int available, boolean closing,
            java.time.Instant observedAt, List<CapacityEntry> entries) {}
    public CapacitySnapshot capacitySnapshot() {
        synchronized (lock) {
            return new CapacitySnapshot(limits.capacity(), entries.size(), limits.capacity() - entries.size(), closing,
                    java.time.Instant.now(), entries.values().stream().map(e -> new CapacityEntry(e.id, e.state.name())).toList());
        }
    }

    /** Future consumer calls only AFTER successful handoff. Never acknowledges active work or another generation. */
    public boolean acknowledge(String runId, UUID token) {
        synchronized (lock) {
            var entry = entries.get(runId);
            if (entry == null || entry.outcome == null || !entry.token.equals(token)
                    || lifecycle != null && !entry.outcome.lifecycleConfirmed()) return false;
            entry.state = State.ACKNOWLEDGED; entry.outcome = null; entry.input = null; entry.finished = null; entries.remove(runId);
            return true;
        }
    }

    private void releaseUnadmitted(Entry entry) {
        if (entries.get(entry.id) == entry) entries.remove(entry.id);
        entry.input = null; entry.state = State.CLOSED;
        entry.finished = null; // Completion signals are exposed only after admission.
    }

    @Override public void close() {
        synchronized (lock) {
            if (closing) return;
            closing = true;
            for (var entry : new ArrayList<>(entries.values())) if (!entry.admitted) releaseUnadmitted(entry);
            executor.shutdown(); // Drain accepted tasks. No shutdownNow/Thread.stop or fabricated cancellation.
        }
        try {
            if (!executor.awaitTermination(limits.shutdownGraceMillis(), TimeUnit.MILLISECONDS)) {
                synchronized (lock) {
                    var pending = entries.values().stream().filter(e -> e.state == State.RUNNING || e.state == State.SUBMITTED)
                            .map(e -> e.id).toList();
                    log.warn("Execution grace elapsed; workers may still run and recovery may be required: runIds={}", pending);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); log.warn("Execution shutdown wait interrupted; no completion/cancellation claim");
        } finally { if (maintenance != null) maintenance.shutdown(); }
    }

    private static ApplicationException unavailable() { return new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE); }
    private static final class Entry {
        final String id;
        final UUID token = UUID.randomUUID();
        final ExecutionOwnership owner;
        State state = State.RESERVED;
        boolean admitted;
        PreparedRun input;
        Outcome outcome;
        RunFailureCode failureCode;
        CompletableFuture<Outcome> finished = new CompletableFuture<>();
        Entry(String id, PreparedRun input, ExecutionOwnership owner) { this.id = id; this.input = input; this.owner = owner; }
    }
}
