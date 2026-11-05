package vn.edu.wspm.backend.services.dataset.csv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.wspm.backend.repositories.ProductRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** ID-only, bounded queries; exact Java matching removes collation-equivalent candidates. */
@Slf4j
@Component
public class RepositoryProductReferenceLookup implements ProductReferenceLookup {
    private static final int QUERY_BATCH_SIZE = 256;
    private final ProductRepository repository;

    public RepositoryProductReferenceLookup(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findExisting(Set<String> requestedIds) {
        Objects.requireNonNull(requestedIds, "requestedIds");
        var ids = new ArrayList<>(requestedIds);
        ids.forEach(id -> Objects.requireNonNull(id, "productId"));
        // Sorting also makes query batching and returned iteration order reproducible.
        ids.sort(String::compareTo);
        var existing = new LinkedHashSet<String>();
        for (int offset = 0; offset < ids.size(); offset += QUERY_BATCH_SIZE) {
            var batch = ids.subList(offset, Math.min(ids.size(), offset + QUERY_BATCH_SIZE));
            var candidates = new HashSet<>(repository.findReferenceCandidates(batch));
            for (String id : batch) if (candidates.contains(id)) existing.add(id);
        }
        log.debug("Product reference lookup completed: requestedCount={}, matchedCount={}", ids.size(), existing.size());
        return Collections.unmodifiableSet(existing);
    }
}
