package vn.edu.wspm.backend.services.dataset.csv;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;

/** Row-level business-valid data, not order consistency, cleaning or snapshot approval. */
public record ValidatedOrderRow(
        long recordNumber, long startLine, long endLine,
        String orderId, BigInteger orderItemId, String customerCode, Instant orderedAt,
        String status, String productId, String skuSnapshot, String productNameSnapshot,
        long quantity, BigDecimal listUnitPrice, BigDecimal allocatedDiscountAmount,
        BigDecimal lineTotal, String currencyCode) {
}
