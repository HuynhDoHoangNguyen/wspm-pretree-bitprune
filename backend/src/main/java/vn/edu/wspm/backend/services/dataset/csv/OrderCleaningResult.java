package vn.edu.wspm.backend.services.dataset.csv;

import java.util.List;

/** Internal result/provenance, not a persisted validation status or an HTTP DTO. */
public record OrderCleaningResult(String policyVersion, Outcome outcome, List<CleanedOrder> orders,
                                  List<RejectedOrder> rejectedOrders, List<Disposition> dispositions,
                                  OrderConsistencyChecker.Result source) {
    public enum Outcome { READY, EMPTY_INPUT, EMPTY_AFTER_CLEANING, BLOCKED_UNATTRIBUTABLE_ORDER }
    public enum Action { KEPT, REJECTED_ORDER, DEDUPLICATED, BLOCKED }
    public enum Reason {
        BUSINESS_RULE_FAILURE, ORDER_FIELD_CONFLICT, LINE_ID_CONTENT_CONFLICT, LINE_ID_ORDER_CONFLICT,
        IDENTICAL_LINE_REPETITION, UNATTRIBUTABLE_ORDER_ID, SNAPSHOT_BLOCKED
    }

    /** Representative is self for KEPT, the retained origin for DEDUPLICATED, null otherwise. */
    public record Disposition(OrderConsistencyIssue.Position position, Action action,
                              OrderConsistencyIssue.Position representative, List<Reason> reasons) {
        public Disposition { reasons = List.copyOf(reasons); }
    }

    public record RejectedOrder(String orderId, List<Reason> reasons, List<OrderConsistencyIssue.Position> positions) {
        public RejectedOrder {
            reasons = List.copyOf(reasons);
            positions = List.copyOf(positions);
        }
        @Override public String toString() {
            return "RejectedOrder[reasons=" + reasons + ", rowCount=" + positions.size() + "]";
        }
    }

    public OrderCleaningResult {
        orders = List.copyOf(orders);
        rejectedOrders = List.copyOf(rejectedOrders);
        dispositions = List.copyOf(dispositions);
    }

    /** Cleaning readiness only; does not approve a snapshot or configure/execute mining. */
    public boolean hasUsableOrders() { return outcome == Outcome.READY; }
    public long count(Action action) { return dispositions.stream().filter(row -> row.action() == action).count(); }

    @Override public String toString() {
        return "OrderCleaningResult[policyVersion=" + policyVersion + ", outcome=" + outcome
                + ", orderCount=" + orders.size() + ", rejectedOrderCount=" + rejectedOrders.size()
                + ", sourceRowCount=" + dispositions.size() + "]";
    }
}
