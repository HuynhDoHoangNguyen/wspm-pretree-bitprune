package vn.edu.wspm.backend.services.dataset.conversion;

import java.util.List;
import java.util.Objects;

/** Ordered commercial-data view; no SIDs, core sequences or serialized artifacts yet. */
public record OrderedDataset(List<OrderedCustomer> customers) {
    public static final String POLICY_VERSION = "CUSTOMER_ORDER_TIME_CODEPOINT_V1";

    public OrderedDataset { customers = List.copyOf(customers); }

    /** Orders reference the original immutable grouping objects, including unchanged source lineage. */
    public record OrderedCustomer(String customerCode, List<GroupedDataset.GroupedOrder> orders) {
        public OrderedCustomer {
            Objects.requireNonNull(customerCode);
            orders = List.copyOf(orders);
        }
        @Override public String toString() { return "OrderedCustomer[orders=" + orders.size() + "]"; }
    }

    @Override public String toString() { return "OrderedDataset[customers=" + customers.size() + "]"; }
}
