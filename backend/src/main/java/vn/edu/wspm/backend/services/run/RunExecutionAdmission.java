package vn.edu.wspm.backend.services.run;

/**
 * Managed-execution port with an opt-in WDM-05.4 provider (absent in WDM-05.3).
 * reserve must be bounded/nonblocking and reserve actual capacity; never execute the job yet.
 * activate is called only after durable commit and must acknowledge managed ownership before
 * returning. On exception its outcome may be unknown: the caller must not retry or delete the run.
 */
public interface RunExecutionAdmission {
    Reservation reserve(String runId, PreparedRun input);

    interface Reservation extends AutoCloseable {
        /** Empty only for legacy/test admission; the production managed provider always supplies ownership. */
        default java.util.Optional<vn.edu.wspm.backend.services.run.execution.ExecutionOwnership> ownership() {
            return java.util.Optional.empty();
        }
        void activate();
        /** Idempotent, nonthrowing release of an unactivated reservation; never cancel activated work. */
        @Override void close();
    }
}
