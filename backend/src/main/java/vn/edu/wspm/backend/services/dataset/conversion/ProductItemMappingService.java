package vn.edu.wspm.backend.services.dataset.conversion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.dataset.ProductItemMappingStore;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Consumes only kept records from the admission proof; does not claim or execute conversion. */
@Service
@Slf4j
public class ProductItemMappingService {
    private static final int MAX_ATTEMPTS = 4;
    private final ProductItemMappingStore store;

    public ProductItemMappingService(ProductItemMappingStore store) { this.store = store; }

    public ProductItemDictionary resolve(VerifiedConversionInput input) {
        Objects.requireNonNull(input, "verified input");
        // String natural order (UTF-16 lexicographic), independent of DB collation and input row order.
        var products = new TreeSet<String>();
        for (var record : input.keptRecords()) {
            if (record.fields().size() != 13) throw new IllegalArgumentException("Invalid verified record width");
            String product = record.fields().get(5);
            if (product.isBlank() || product.codePointCount(0, product.length()) > 40) {
                throw new IllegalArgumentException("Invalid verified product ID");
            }
            products.add(product);
        }
        if (products.isEmpty()) return new ProductItemDictionary(java.util.Map.of());
        var ordered = List.copyOf(products);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                var dictionary = store.resolve(ordered);
                log.info("Product item mapping resolved: products={}, attempt={}", products.size(), attempt);
                return dictionary;
            } catch (DuplicateKeyException | ConcurrencyFailureException conflict) {
                // Failed transaction has rolled back before the repository proxy returns here.
                log.warn("Product item mapping contention: attempt={}", attempt);
            } catch (DataAccessException failure) {
                // SQL exception messages can contain product IDs; retain safe stack location only.
                var safe = new IllegalStateException("Mapping persistence failed: " + failure.getClass().getSimpleName());
                safe.setStackTrace(failure.getStackTrace());
                log.error("Product item mapping unavailable", safe);
                throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
            } catch (ApplicationException rejected) {
                log.warn("Product item mapping rejected: code={}", rejected.code());
                throw rejected;
            }
        }
        throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE, "Product item mapping is busy; retry later.");
    }
}
