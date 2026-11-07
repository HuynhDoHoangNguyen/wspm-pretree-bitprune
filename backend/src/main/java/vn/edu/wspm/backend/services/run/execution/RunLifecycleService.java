package vn.edu.wspm.backend.services.run.execution;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import vn.edu.wspm.backend.repositories.run.RunLifecycleRepository;

/** Reconciliation retries DB operations only, never mining. A false confirmation retains the outcome/slot. */
@Slf4j
public class RunLifecycleService {
    private final RunLifecycleRepository repository;
    private final LifecycleLimits limits;
    private final String ownerId = UUID.randomUUID().toString();
    public RunLifecycleService(RunLifecycleRepository repository, LifecycleLimits limits) {
        this.repository = repository; this.limits = limits;
    }
    public ExecutionOwnership newOwnership() { return new ExecutionOwnership(ownerId, UUID.randomUUID().toString(), limits.leaseMillis()); }
    public long heartbeatMillis() { return limits.heartbeatMillis(); }

    public boolean claim(String id, ExecutionOwnership owner) {
        try {
            // Zero rows is rejection, NOT permission to rerun an already RUNNING attempt.
            if (repository.claim(id, owner) != 1) return false;
            log.info("Run entered RUNNING: runId={}", id); return true;
        } catch (RuntimeException uncertain) {
            unavailable("claim", id, uncertain);
            // Only the original invocation reaches here. Manager's per-entry execution guard prevents replay.
            var observed = read(id);
            return observed != null && observed.matches(owner) && observed.leaseValid() && "RUNNING".equals(observed.status());
        }
    }
    public boolean confirmSuccess(String id, ExecutionOwnership owner, RunMetricsCapture capture) {
        var expected = java.util.Objects.requireNonNull(capture).persisted();
        try { repository.succeed(id, owner, capture); }
        catch (RuntimeException uncertain) { unavailable("success", id, uncertain); }
        var observed = read(id);
        boolean confirmed = observed != null && observed.matches(owner) && "SUCCEEDED".equals(observed.status())
                && observed.failureCode() == null && expected.equals(observed.metrics());
        if (confirmed) log.debug("Run SUCCEEDED confirmed: runId={}", id);
        return confirmed;
    }
    public boolean confirmFailure(String id, ExecutionOwnership owner, RunFailureCode code) {
        var observed = read(id);
        if (failed(observed, owner)) return true;
        if (observed == null || !observed.matches(owner) || !observed.leaseValid()
                || !("QUEUED".equals(observed.status()) || "RUNNING".equals(observed.status()))) return false;
        try { repository.fail(id, owner, observed.status(), code); }
        catch (RuntimeException uncertain) { unavailable("failure", id, uncertain); }
        return failed(read(id), owner);
    }
    private boolean failed(RunLifecycleRepository.Observation observed, ExecutionOwnership owner) {
        if (observed == null || !observed.matches(owner) || !"FAILED".equals(observed.status())) return false;
        try { RunFailureCode.valueOf(observed.failureCode()); return true; } catch (RuntimeException unknown) { return false; }
    }
    public void renew(String id, ExecutionOwnership owner) {
        try { repository.renew(id, owner); } catch (RuntimeException unavailable) { unavailable("heartbeat", id, unavailable); }
    }
    public void recover() {
        try { int count = repository.recover(limits.recoveryBatch()); if (count > 0) log.warn("Expired owned executions recovered: count={}", count); }
        catch (RuntimeException unavailable) { unavailable("recovery", "batch", unavailable); }
    }
    private RunLifecycleRepository.Observation read(String id) {
        try { return repository.read(id); } catch (RuntimeException unavailable) { unavailable("readback", id, unavailable); return null; }
    }
    private void unavailable(String operation, String id, RuntimeException failure) {
        var safe = new IllegalStateException("Lifecycle unavailable: " + failure.getClass().getSimpleName());
        safe.setStackTrace(failure.getStackTrace()); log.warn("Lifecycle reconciliation pending: operation={}, runId={}", operation, id, safe);
    }
}
