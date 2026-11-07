package vn.edu.wspm.backend.services.run.engine;

import java.util.Objects;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.model.SequenceDatabase;

/**
 * Structural input only. Construction does not verify a snapshot or approve execution.
 * Callers supply an immutable database parsed from freshly verified SPMF publication bytes.
 * Zero/underflow eligibility is checked by the adapter using the existing resolver and gate.
 */
public record MiningEngineRequest(
        MiningAlgorithm algorithm, SequenceDatabase database, MinimumSupport minimumSupport) {
    public MiningEngineRequest {
        Objects.requireNonNull(algorithm, "algorithm is required");
        Objects.requireNonNull(database, "database is required");
        Objects.requireNonNull(minimumSupport, "minimum support is required");
        if (database.size() == 0) {
            throw new IllegalArgumentException("Web mining requires a non-empty database");
        }
    }
}
