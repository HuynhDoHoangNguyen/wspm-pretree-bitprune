package vn.edu.wspm.backend.services.run.execution;

import java.util.*;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.services.run.persistence.RunResultPublicationService;

/** Single bounded pass; work runs outside registry locks. Permanent failures retain the original result. */
@Slf4j
public final class RunResultPublicationConsumer implements AutoCloseable {
    private final ManagedRunExecutionAdmission admission;
    private final RunResultPublicationService publication;
    private final Map<String,UUID> blocked=new HashMap<>();
    private final Map<String,Integer> attempts=new HashMap<>();
    private final Map<String,Long> nextAttempt=new HashMap<>();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r -> {
        var t=new Thread(r,"wdm-result-publication"); t.setDaemon(true); return t;
    });
    public RunResultPublicationConsumer(ManagedRunExecutionAdmission admission,RunResultPublicationService publication) {
        this.admission=admission; this.publication=publication;
    }
    public void start() { worker.scheduleWithFixedDelay(this::safely,1,2,TimeUnit.SECONDS); }
    synchronized void pass() {
        var entries=admission.capacitySnapshot().entries();
        blocked.keySet().retainAll(entries.stream().map(ManagedRunExecutionAdmission.CapacityEntry::runId).toList());
        attempts.keySet().retainAll(entries.stream().map(ManagedRunExecutionAdmission.CapacityEntry::runId).toList());
        nextAttempt.keySet().retainAll(entries.stream().map(ManagedRunExecutionAdmission.CapacityEntry::runId).toList());
        for (var entry:entries) {
            if (worker.isShutdown()) return;
            var outcome=admission.outcome(entry.runId()).orElse(null);
            if (outcome==null || outcome.result()==null || !outcome.lifecycleConfirmed() || outcome.token().equals(blocked.get(entry.runId()))) continue;
            Long due=nextAttempt.get(entry.runId());
            if (due!=null && System.nanoTime()-due<0) continue;
            try {
                publication.publish(outcome);
                boolean acknowledged=admission.acknowledge(outcome.runId(),outcome.token());
                log.info("Published result handoff: runId={}, acknowledged={}",outcome.runId(),acknowledged);
            } catch (ApplicationException e) {
                if (!"SERVICE_UNAVAILABLE".equals(e.code())) blocked.put(outcome.runId(),outcome.token());
                else retry(outcome);
                log.warn("Result handoff retained: runId={}, code={}",outcome.runId(),e.code());
            } catch (IllegalArgumentException e) {
                blocked.put(outcome.runId(),outcome.token()); log.warn("Result handoff rejected: runId={}",outcome.runId());
            } catch (RuntimeException e) {
                retry(outcome);
                log.warn("Result handoff temporarily unavailable: runId={}, type={}",outcome.runId(),e.getClass().getSimpleName());
            }
        }
    }
    /** Explicit Java-only reconciliation after an operator resolves a publication failure; never re-mines. */
    public synchronized boolean retryRetained(String runId, UUID token) {
        if (worker.isShutdown() || token == null || !token.equals(blocked.get(runId))) return false;
        var outcome=admission.outcome(runId).orElse(null);
        if (outcome==null || !token.equals(outcome.token()) || !outcome.lifecycleConfirmed() || outcome.result()==null) return false;
        blocked.remove(runId); attempts.remove(runId); nextAttempt.remove(runId);
        log.info("Retained publication retry requested: runId={}",runId);
        return true;
    }
    private void retry(ManagedRunExecutionAdmission.Outcome outcome) {
        int n=attempts.merge(outcome.runId(),1,Integer::sum);
        if (n>=5) blocked.put(outcome.runId(),outcome.token());
        else nextAttempt.put(outcome.runId(),System.nanoTime()+TimeUnit.SECONDS.toNanos(2L<<n));
    }
    private void safely() { try {pass();} catch(RuntimeException e) {log.warn("Result handoff pass unavailable: type={}",e.getClass().getSimpleName());} }
    @Override public void close() {
        worker.shutdown();
        try { if (!worker.awaitTermination(30,TimeUnit.SECONDS)) log.warn("Result handoff shutdown pending; no acknowledgement is fabricated"); }
        catch (InterruptedException e) {Thread.currentThread().interrupt();}
    }
}
