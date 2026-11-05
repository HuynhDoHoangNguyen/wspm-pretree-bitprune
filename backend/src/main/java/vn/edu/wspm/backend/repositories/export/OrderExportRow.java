package vn.edu.wspm.backend.repositories.export;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;

/** Historical read projection for wdm-order-csv-v1; not a public JSON DTO. */
public record OrderExportRow(
        String orderId,
        BigInteger orderItemId,
        String customerCode,
        Instant orderedAt,
        String status,
        String productId,
        String skuSnapshot,
        String productNameSnapshot,
        long quantity,
        BigDecimal listUnitPrice,
        BigDecimal allocatedDiscountAmount,
        BigDecimal lineTotal,
        String currencyCode) {
}
