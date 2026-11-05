package vn.edu.wspm.backend.services.dataset.csv;

import java.time.Instant;
import java.util.List;

/** Internal commercial-data view; product groups do not aggregate money/quantity or assign mining IDs. */
public record CleanedOrder(String orderId, String customerCode, Instant orderedAt, String currencyCode,
                           List<Line> lines, List<ProductGroup> products) {
    public record Line(ValidatedOrderRow values, List<OrderConsistencyIssue.Position> origins) {
        public Line { origins = List.copyOf(origins); }
        @Override public String toString() {
            return "CleanedLine[recordNumber=" + values.recordNumber() + ", originCount=" + origins.size() + "]";
        }
    }

    public record ProductGroup(String productId, List<Line> lines) {
        public ProductGroup { lines = List.copyOf(lines); }
        @Override public String toString() { return "ProductGroup[lineCount=" + lines.size() + "]"; }
    }

    public CleanedOrder {
        lines = List.copyOf(lines);
        products = List.copyOf(products);
    }

    @Override public String toString() {
        return "CleanedOrder[lineCount=" + lines.size() + ", productCount=" + products.size() + "]";
    }
}
