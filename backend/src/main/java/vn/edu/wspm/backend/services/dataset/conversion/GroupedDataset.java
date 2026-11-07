package vn.edu.wspm.backend.services.dataset.conversion;

import vn.edu.wspm.backend.services.dataset.csv.ParsedCsvRecord;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable membership/provenance view. Map encounter order is NOT sequence chronology or SID order. */
public record GroupedDataset(Map<String, CustomerGroup> customers) {
    public static final String POLICY_VERSION = "CUSTOMER_ORDER_ITEMSET_V1";

    public GroupedDataset { customers = frozen(customers); }

    public record CustomerGroup(String customerCode, Map<String, GroupedOrder> orders) {
        public CustomerGroup {
            Objects.requireNonNull(customerCode);
            orders = frozen(orders);
        }
        @Override public String toString() { return "CustomerGroup[orders=" + orders.size() + "]"; }
    }

    /** Source records retain distinct lines, quantities, historical text/money and original positions. */
    public record GroupedOrder(String orderId, Instant orderedAt, String currencyCode,
                               List<Integer> itemIds, List<ParsedCsvRecord> sourceRecords) {
        public GroupedOrder {
            Objects.requireNonNull(orderId);
            Objects.requireNonNull(orderedAt);
            Objects.requireNonNull(currencyCode);
            itemIds = List.copyOf(itemIds);
            sourceRecords = List.copyOf(sourceRecords);
        }
        @Override public String toString() {
            return "GroupedOrder[items=" + itemIds.size() + ", records=" + sourceRecords.size() + "]";
        }
    }

    private static <K, V> Map<K, V> frozen(Map<K, V> source) {
        source.forEach((key, value) -> { Objects.requireNonNull(key); Objects.requireNonNull(value); });
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    @Override public String toString() { return "GroupedDataset[customers=" + customers.size() + "]"; }
}
