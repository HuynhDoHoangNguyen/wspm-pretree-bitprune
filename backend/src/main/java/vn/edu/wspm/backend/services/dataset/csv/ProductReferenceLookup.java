package vn.edu.wspm.backend.services.dataset.csv;

import java.util.Set;

/** Read-only catalog boundary. Failures propagate, never become missing-product results. */
public interface ProductReferenceLookup {
    Set<String> findExisting(Set<String> requestedIds);
}
