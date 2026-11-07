package vn.edu.wspm.backend.repositories.dataset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.services.dataset.conversion.ProductItemDictionary;

import java.util.LinkedHashMap;
import java.util.List;

/** Append-only global mapping writer. Every retry must enter a fresh transaction through this bean. */
@Repository
public class ProductItemMappingStore {
    private final JdbcTemplate jdbc;

    public ProductItemMappingStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public ProductItemDictionary resolve(List<String> sortedProducts) {
        // Read UNSIGNED values as long, never through the legacy Integer JPA mapping.
        long maximum = jdbc.queryForObject("SELECT COALESCE(MAX(item_id),0) FROM ITEM_MAPPING", Long.class);
        if (maximum > Integer.MAX_VALUE) throw invalid();
        var mappings = new LinkedHashMap<String, Integer>();
        for (String product : sortedProducts) {
            // Collation equality may match a different opaque ID. Require exact Java equality as well.
            var catalog = jdbc.query("SELECT product_id FROM PRODUCT WHERE product_id=?",
                    (rs, row) -> rs.getString(1), product);
            if (catalog.size() != 1 || !product.equals(catalog.getFirst())) throw invalid();
            var existing = jdbc.query("SELECT product_id,item_id FROM ITEM_MAPPING WHERE product_id=?",
                    (rs, row) -> new Entry(rs.getString(1), rs.getLong(2)), product);
            if (!existing.isEmpty()) {
                var entry = existing.getFirst();
                if (existing.size() != 1 || !product.equals(entry.product())
                        || entry.item() <= 0 || entry.item() > Integer.MAX_VALUE) throw invalid();
                mappings.put(product, (int) entry.item());
            } else {
                if (maximum == Integer.MAX_VALUE) throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE,
                        "Product item ID range is exhausted.");
                int item = (int) ++maximum;
                jdbc.update("INSERT INTO ITEM_MAPPING(product_id,item_id) VALUES (?,?)", product, item);
                mappings.put(product, item);
            }
        }
        return new ProductItemDictionary(mappings);
    }

    private record Entry(String product, long item) {}
    private static ApplicationException invalid() {
        return new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE,
                "Product item mapping is incompatible with the verified input or mining item range.");
    }
}
