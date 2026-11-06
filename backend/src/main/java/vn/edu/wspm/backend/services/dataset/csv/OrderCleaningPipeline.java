package vn.edu.wspm.backend.services.dataset.csv;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static vn.edu.wspm.backend.services.dataset.csv.OrderCleaningResult.Action.*;
import static vn.edu.wspm.backend.services.dataset.csv.OrderCleaningResult.Outcome.*;
import static vn.edu.wspm.backend.services.dataset.csv.OrderCleaningResult.Reason.*;

/** Applies the owner-approved application policy to a trusted complete WDM-02.3 result. */
public final class OrderCleaningPipeline {
    public static final String POLICY_VERSION = "WDM_ORDER_CLEANING_V1";

    public OrderCleaningResult clean(OrderConsistencyChecker.Result source) {
        Objects.requireNonNull(source, "source");
        // Fail closed if a caller supplies incomplete/stale checker findings. Reuse 02.3, never copy its rules.
        if (!new OrderConsistencyChecker().check(source.rows()).equals(source)) {
            throw new IllegalArgumentException("Cleaning requires the matching complete consistency result");
        }
        if (source.rows().stream().anyMatch(row -> !row.usable("order_id"))) return blocked(source);

        var byRecord = new LinkedHashMap<Long, OrderConsistencyInput>();
        var orders = new LinkedHashMap<String, List<OrderConsistencyInput>>();
        var rejections = new LinkedHashMap<String, EnumSet<OrderCleaningResult.Reason>>();
        for (var row : source.rows()) {
            byRecord.put(row.source().recordNumber(), row);
            String orderId = row.source().fields().getFirst();
            orders.computeIfAbsent(orderId, ignored -> new ArrayList<>()).add(row);
            if (!row.business().valid()) reject(rejections, orderId, BUSINESS_RULE_FAILURE);
        }
        // Every rejection is established before any line is selected/deduplicated.
        for (var issue : source.issues()) {
            OrderCleaningResult.Reason reason = switch (issue.code()) {
                case ORDER_FIELD_CONFLICT -> ORDER_FIELD_CONFLICT;
                case LINE_ID_CONTENT_CONFLICT -> LINE_ID_CONTENT_CONFLICT;
                case LINE_ID_ORDER_CONFLICT -> LINE_ID_ORDER_CONFLICT;
                case DUPLICATE_LINE_ID -> null;
            };
            if (reason == null) continue;
            for (var position : issue.positions()) {
                var row = byRecord.get(position.recordNumber());
                reject(rejections, row.source().fields().getFirst(), reason);
            }
        }

        var cleaned = new ArrayList<CleanedOrder>();
        var rejected = new ArrayList<OrderCleaningResult.RejectedOrder>();
        var dispositions = new ArrayList<OrderCleaningResult.Disposition>();
        for (var entry : orders.entrySet()) {
            var reasons = rejections.get(entry.getKey());
            if (reasons != null) {
                var fixedReasons = List.copyOf(reasons);
                var positions = entry.getValue().stream().map(this::position).toList();
                rejected.add(new OrderCleaningResult.RejectedOrder(entry.getKey(), fixedReasons, positions));
                for (var location : positions) dispositions.add(new OrderCleaningResult.Disposition(
                        location, REJECTED_ORDER, null, fixedReasons));
            } else cleaned.add(cleanOrder(entry.getValue(), dispositions));
        }
        dispositions.sort(Comparator.comparingLong(row -> row.position().recordNumber()));
        var outcome = !cleaned.isEmpty() ? READY : source.rows().isEmpty() ? EMPTY_INPUT : EMPTY_AFTER_CLEANING;
        return new OrderCleaningResult(POLICY_VERSION, outcome, cleaned, rejected, dispositions, source);
    }

    private CleanedOrder cleanOrder(List<OrderConsistencyInput> rows,
                                   List<OrderCleaningResult.Disposition> dispositions) {
        var byLine = new LinkedHashMap<BigInteger, List<OrderConsistencyInput>>();
        for (var row : rows) {
            byLine.computeIfAbsent(row.business().row().orderItemId(), ignored -> new ArrayList<>()).add(row);
        }
        var lines = new ArrayList<CleanedOrder.Line>();
        var products = new LinkedHashMap<String, List<CleanedOrder.Line>>();
        for (var group : byLine.values()) {
            var representative = group.getFirst();
            var location = position(representative);
            var origins = group.stream().map(this::position).toList();
            var line = new CleanedOrder.Line(representative.business().row(), origins);
            lines.add(line);
            products.computeIfAbsent(line.values().productId(), ignored -> new ArrayList<>()).add(line);
            for (var row : group) {
                // Exact source payload match, including all historical values, is mandatory for deduplication.
                if (!row.source().fields().equals(representative.source().fields())) {
                    throw new IllegalArgumentException("Retained duplicate payloads must be identical");
                }
                boolean kept = row.source().recordNumber() == representative.source().recordNumber();
                dispositions.add(new OrderCleaningResult.Disposition(position(row), kept ? KEPT : DEDUPLICATED,
                        location, kept ? List.of() : List.of(IDENTICAL_LINE_REPETITION)));
            }
        }
        var metadata = rows.getFirst().business().row();
        var productGroups = products.entrySet().stream()
                .map(entry -> new CleanedOrder.ProductGroup(entry.getKey(), entry.getValue())).toList();
        return new CleanedOrder(metadata.orderId(), metadata.customerCode(), metadata.orderedAt(),
                metadata.currencyCode(), lines, productGroups);
    }

    private OrderCleaningResult blocked(OrderConsistencyChecker.Result source) {
        var dispositions = source.rows().stream().map(row -> new OrderCleaningResult.Disposition(
                position(row), BLOCKED, null, List.of(row.usable("order_id") ? SNAPSHOT_BLOCKED : UNATTRIBUTABLE_ORDER_ID)))
                .toList();
        // No partial clean output or processed rejection list; original findings remain available in source.
        return new OrderCleaningResult(POLICY_VERSION, BLOCKED_UNATTRIBUTABLE_ORDER,
                List.of(), List.of(), dispositions, source);
    }

    private void reject(Map<String, EnumSet<OrderCleaningResult.Reason>> rejections, String orderId,
                        OrderCleaningResult.Reason reason) {
        rejections.computeIfAbsent(orderId, ignored -> EnumSet.noneOf(OrderCleaningResult.Reason.class)).add(reason);
    }

    private OrderConsistencyIssue.Position position(OrderConsistencyInput row) {
        return new OrderConsistencyIssue.Position(row.source().recordNumber(), row.source().startLine(), row.source().endLine());
    }
}
