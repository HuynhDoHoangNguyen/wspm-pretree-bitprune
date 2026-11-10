package vn.edu.wspm.backend.services.run.engine;

/**
 * Synchronous Java boundary, implemented in WDM-05.2; not an HTTP endpoint or job queue.
 * Implementations must resolve against request.database().size() and apply the existing
 * RunConfigurationValidityGate before invoking a miner. They own and close a fresh sampler
 * per call, propagate failures, and return only complete successful results.
 * Snapshot authorization/integrity, parsing, scheduling and persistence belong to callers.
 */
public interface MiningEngine {
    MiningEngineResult execute(MiningEngineRequest request);
}
