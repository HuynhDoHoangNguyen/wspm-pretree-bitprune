package vn.edu.wspm.backend.services.run.persistence;

import java.util.Objects;
import vn.edu.wspm.backend.services.run.PreparedRun;

/** Small retained handoff identity; artifact re-verification is still required before later publication. */
public record RunPersistenceContext(String runId, RunProvenance provenance) {
    public RunPersistenceContext {
        if (runId == null || runId.isBlank() || runId.length() > 40) throw new IllegalArgumentException("Invalid run identity");
        Objects.requireNonNull(provenance);
    }
    public static RunPersistenceContext from(String runId, PreparedRun input) {
        return new RunPersistenceContext(runId, RunProvenance.from(input));
    }
    @Override public String toString() { return "RunPersistenceContext[runId=" + runId + "]"; }
}
