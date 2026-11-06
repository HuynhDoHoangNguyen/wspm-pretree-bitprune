package vn.edu.wspm.backend.services.dataset.conversion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;

/** Pure ordering boundary after grouping. Equal timestamps retain separate orders using an opaque-ID tie-break. */
@Service
@Slf4j
public class TemporalOrderingService {
    private static final Comparator<String> CODE_POINT_ORDER = TemporalOrderingService::compareCodePoints;
    private static final Comparator<GroupedDataset.GroupedOrder> ORDER = Comparator
            .comparing(GroupedDataset.GroupedOrder::orderedAt)
            .thenComparing(GroupedDataset.GroupedOrder::orderId, CODE_POINT_ORDER);

    public OrderedDataset order(GroupedDataset input) {
        Objects.requireNonNull(input, "grouped input");
        var customers = new ArrayList<OrderedDataset.OrderedCustomer>();
        var orderIds = new HashSet<String>();
        for (var entry : input.customers().entrySet()) {
            var customer = entry.getValue();
            if (entry.getKey().isBlank() || !entry.getKey().equals(customer.customerCode()) || customer.orders().isEmpty()) {
                throw invalid("CUSTOMER_STRUCTURE");
            }
            var orders = new ArrayList<GroupedDataset.GroupedOrder>();
            for (var orderEntry : customer.orders().entrySet()) {
                var order = orderEntry.getValue();
                if (orderEntry.getKey().isBlank() || !orderEntry.getKey().equals(order.orderId())
                        || !orderIds.add(order.orderId()) || order.itemIds().isEmpty() || order.sourceRecords().isEmpty()) {
                    throw invalid("ORDER_STRUCTURE");
                }
                int previous = 0;
                for (int item : order.itemIds()) {
                    if (item <= previous) throw invalid("ITEMSET_STRUCTURE");
                    previous = item;
                }
                orders.add(order);
            }
            orders.sort(ORDER);
            customers.add(new OrderedDataset.OrderedCustomer(customer.customerCode(), orders));
        }
        customers.sort(Comparator.comparing(OrderedDataset.OrderedCustomer::customerCode, CODE_POINT_ORDER));
        log.info("Temporal ordering completed: customers={}, orders={}", customers.size(), orderIds.size());
        return new OrderedDataset(customers);
    }

    private static int compareCodePoints(String left, String right) {
        int i = 0, j = 0;
        while (i < left.length() && j < right.length()) {
            int a = left.codePointAt(i), b = right.codePointAt(j);
            if (a != b) return Integer.compare(a, b);
            i += Character.charCount(a);
            j += Character.charCount(b);
        }
        // One value is a prefix of the other, or both are identical.
        return Integer.compare(left.length() - i, right.length() - j);
    }

    private static ApplicationException invalid(String reason) {
        log.warn("Temporal ordering rejected: reason={}", reason);
        return new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE, "Grouped ordering input is inconsistent.");
    }
}
