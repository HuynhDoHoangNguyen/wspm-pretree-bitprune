package vn.edu.wspm.backend.services.dataset.csv;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static vn.edu.wspm.backend.services.dataset.csv.OrderConsistencyIssue.Code.*;

/** Cross-row checks for one complete snapshot; no cleaning, database access or snapshot approval. */
public final class OrderConsistencyChecker {
    private static final List<String> COLUMNS = List.of(
            "order_id", "order_item_id", "customer_code", "ordered_at", "status", "product_id",
            "sku_snapshot", "product_name_snapshot", "quantity", "list_unit_price",
            "allocated_discount_amount", "line_total", "currency_code");

    /** Internal result: retains every source/business result, including rows without usable keys. */
    public record Result(List<OrderConsistencyInput> rows, List<OrderConsistencyIssue> issues,
                         Set<String> affectedOrderIds) {
        public Result {
            rows = List.copyOf(rows);
            issues = List.copyOf(issues);
            affectedOrderIds = Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(affectedOrderIds)));
        }
        public boolean hasConsistencyIssues() { return !issues.isEmpty(); }
        @Override public String toString() {
            return "OrderConsistencyResult[rowCount=" + rows.size() + ", issueCount=" + issues.size()
                    + ", affectedOrderCount=" + affectedOrderIds.size() + "]";
        }
    }

    public Result check(Collection<OrderConsistencyInput> inputs) {
        var rows = new ArrayList<>(inputs);
        rows.sort(Comparator.comparingLong(row -> row.source().recordNumber()));
        var orders = new LinkedHashMap<String, List<OrderConsistencyInput>>();
        var lines = new LinkedHashMap<String, List<OrderConsistencyInput>>();
        long previousRecord = -1;
        for (var row : rows) {
            if (row.source().recordNumber() == previousRecord) {
                throw new IllegalArgumentException("Record numbers must be unique within a snapshot");
            }
            previousRecord = row.source().recordNumber();
            index(orders, row, 0);
            index(lines, row, 1);
        }

        var issues = new ArrayList<OrderConsistencyIssue>();
        var affected = new LinkedHashSet<String>();
        for (var order : orders.values()) {
            for (int column : new int[]{2, 3, 12}) {
                if (conflict(order, column, ORDER_FIELD_CONFLICT, issues)) affect(order, affected);
            }
            // Every usable status is already COMPLETED under 02.2; invalid statuses retain their row errors.
        }
        for (var line : lines.values()) {
            if (line.size() < 2) continue;
            issues.add(issue(DUPLICATE_LINE_ID, "order_item_id", line));
            affect(line, affected);
            conflict(line, 0, LINE_ID_ORDER_CONFLICT, issues);
            for (int column = 2; column < COLUMNS.size(); column++) {
                conflict(line, column, LINE_ID_CONTENT_CONFLICT, issues);
            }
        }
        issues.sort(Comparator.comparingLong((OrderConsistencyIssue issue) -> issue.positions().getFirst().recordNumber())
                .thenComparing(OrderConsistencyIssue::code)
                .thenComparingInt(issue -> COLUMNS.indexOf(issue.field())));
        return new Result(rows, issues, affected);
    }

    private void index(Map<String, List<OrderConsistencyInput>> groups, OrderConsistencyInput row, int column) {
        if (row.usable(COLUMNS.get(column))) {
            // CSV-v1 canonical integer/timestamp/decimal spellings allow exact string comparison;
            // no duplicate copy of 02.2's parsing/validation formulas is needed here.
            groups.computeIfAbsent(row.source().fields().get(column), ignored -> new ArrayList<>()).add(row);
        }
    }

    private boolean conflict(List<OrderConsistencyInput> group, int column, OrderConsistencyIssue.Code code,
                             List<OrderConsistencyIssue> issues) {
        var comparable = new ArrayList<OrderConsistencyInput>();
        var values = new LinkedHashSet<String>();
        for (var row : group) {
            if (row.usable(COLUMNS.get(column))) {
                comparable.add(row);
                values.add(row.source().fields().get(column));
            }
        }
        if (values.size() < 2) return false;
        issues.add(issue(code, COLUMNS.get(column), comparable));
        return true;
    }

    private OrderConsistencyIssue issue(OrderConsistencyIssue.Code code, String field, List<OrderConsistencyInput> rows) {
        var positions = rows.stream().map(row -> new OrderConsistencyIssue.Position(
                row.source().recordNumber(), row.source().startLine(), row.source().endLine())).toList();
        return new OrderConsistencyIssue(code, field, positions);
    }

    private void affect(List<OrderConsistencyInput> rows, Set<String> affected) {
        for (var row : rows) if (row.usable("order_id")) affected.add(row.source().fields().getFirst());
    }
}
