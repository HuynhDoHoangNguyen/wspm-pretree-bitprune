package vn.edu.wspm.backend.services.dataset.conversion;

import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.services.dataset.csv.*;
import vn.edu.wspm.backend.services.dataset.validation.ValidationLimits;
import vn.edu.wspm.backend.services.dataset.validation.ValidationReport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

/** Cross-checks the frozen manifest against exact raw records, not against the current catalog/business rules. */
final class ConversionManifestVerifier {
    private static final Set<String> REJECTION_REASONS = Set.of("BUSINESS_RULE_FAILURE", "ORDER_FIELD_CONFLICT",
            "LINE_ID_CONTENT_CONFLICT", "LINE_ID_ORDER_CONFLICT");
    private static final Set<String> FIELDS = Set.of("order_id", "order_item_id", "customer_code", "ordered_at", "status",
            "product_id", "sku_snapshot", "product_name_snapshot", "quantity", "list_unit_price",
            "allocated_discount_amount", "line_total", "currency_code");

    List<ParsedCsvRecord> verify(ValidationReport report, byte[] raw, ValidationLimits limits, Runnable budget) throws IOException {
        require(report.counts() != null && report.manifest().size() <= limits.maxRows());
        var rows = new LinkedHashMap<Long, ParsedCsvRecord>();
        try {
            new OrderCsvParser(limits.maxRecordCharacters()).parse(new ByteArrayInputStream(raw), row -> {
                budget.run();
                if (rows.size() >= limits.maxRows()) throw unavailable();
                rows.put(row.recordNumber(), row);
            });
        } catch (CsvStructureException failure) {
            if (failure.code() == CsvStructureException.Code.CSV_RECORD_LIMIT_EXCEEDED) throw unavailable();
            throw invalid();
        }
        require(rows.size() == report.manifest().size());
        var decisions = new LinkedHashMap<Long, ValidationReport.Decision>();
        var counts = new HashMap<String, Long>();
        var allOrders = new HashSet<String>();
        var keptOrders = new HashSet<String>();
        var rejectedOrders = new HashSet<String>();
        var keptLineIds = new HashSet<String>();
        var orderMetadata = new HashMap<String, List<String>>();
        var rejectedReasons = new HashMap<String, List<String>>();
        var kept = new ArrayList<ParsedCsvRecord>();
        int index = 0;
        for (var row : rows.values()) {
            budget.run();
            var decision = report.manifest().get(index++);
            require(decision != null && position(row).equals(decision.position()));
            String action = decision.action(), orderId = row.fields().getFirst();
            require(action != null && Set.of("KEPT", "DEDUPLICATED", "REJECTED_ORDER").contains(action));
            decisions.put(row.recordNumber(), decision);
            counts.merge(action, 1L, Long::sum);
            allOrders.add(orderId);
            if ("REJECTED_ORDER".equals(action)) {
                require(decision.representative() == null && !decision.reasons().isEmpty()
                        && REJECTION_REASONS.containsAll(decision.reasons())
                        && new HashSet<>(decision.reasons()).size() == decision.reasons().size());
                var previous = rejectedReasons.putIfAbsent(orderId, decision.reasons());
                require(previous == null || previous.equals(decision.reasons()));
                rejectedOrders.add(orderId);
            } else {
                keptOrders.add(orderId);
                var metadata = List.of(row.fields().get(2), row.fields().get(3), row.fields().get(12));
                var previous = orderMetadata.putIfAbsent(orderId, metadata);
                require(previous == null || previous.equals(metadata));
                if ("KEPT".equals(action)) {
                    require(position(row).equals(decision.representative()) && decision.reasons().isEmpty()
                            && keptLineIds.add(row.fields().get(1)));
                    kept.add(row);
                } else require(decision.representative() != null
                        && decision.reasons().equals(List.of("IDENTICAL_LINE_REPETITION")));
            }
        }
        require(!kept.isEmpty() && Collections.disjoint(keptOrders, rejectedOrders));
        for (var decision : decisions.values()) {
            budget.run();
            if (!"DEDUPLICATED".equals(decision.action())) continue;
            var representative = decisions.get(decision.representative().recordNumber());
            require(representative != null && "KEPT".equals(representative.action())
                    && representative.position().equals(decision.representative())
                    && representative.position().recordNumber() < decision.position().recordNumber()
                    && rows.get(decision.position().recordNumber()).fields()
                        .equals(rows.get(representative.position().recordNumber()).fields()));
        }
        var businessRows = new HashSet<Long>();
        for (var diagnostic : report.diagnostics()) {
            budget.run();
            require(diagnostic != null && "RECORD_RANGE".equals(diagnostic.positionAccuracy())
                    && diagnostic.field() != null && FIELDS.contains(diagnostic.field()) && !diagnostic.positions().isEmpty());
            boolean business = "BUSINESS".equals(diagnostic.stage());
            boolean duplicate = "DUPLICATE_LINE_ID".equals(diagnostic.code());
            try {
                if (business) {
                    BusinessValidationIssue.Code.valueOf(diagnostic.code());
                    require(diagnostic.positions().size() == 1);
                } else {
                    require("CONSISTENCY".equals(diagnostic.stage()));
                    OrderConsistencyIssue.Code.valueOf(diagnostic.code());
                }
            } catch (IllegalArgumentException | NullPointerException malformed) { throw invalid(); }
            var seen = new HashSet<Long>();
            for (var p : diagnostic.positions()) {
                var row = p == null ? null : rows.get(p.recordNumber());
                require(row != null && position(row).equals(p) && seen.add(p.recordNumber()));
                if (business) businessRows.add(p.recordNumber());
                if (business || !duplicate) require("REJECTED_ORDER".equals(decisions.get(p.recordNumber()).action()));
            }
        }
        var c = report.counts();
        require(number(c.parsedRecords()) == rows.size() && number(c.inputRecords()) == rows.size()
                && number(c.inputOrders()) == allOrders.size() && number(c.knownOrders()) == allOrders.size()
                && number(c.keptOrders()) == keptOrders.size() && number(c.rejectedOrders()) == rejectedOrders.size()
                && number(c.keptRecords()) == counts.getOrDefault("KEPT", 0L)
                && number(c.rejectedRecords()) == counts.getOrDefault("REJECTED_ORDER", 0L)
                && number(c.deduplicatedRecords()) == counts.getOrDefault("DEDUPLICATED", 0L)
                && number(c.blockedRecords()) == 0 && number(c.businessInvalidRecords()) == businessRows.size()
                && number(c.issueCount()) == report.diagnostics().size());
        return List.copyOf(kept);
    }

    private long number(String value) {
        require(value != null && value.length() <= 19 && value.matches("0|[1-9][0-9]*"));
        try { return Long.parseLong(value); } catch (NumberFormatException malformed) { throw invalid(); }
    }
    private OrderConsistencyIssue.Position position(ParsedCsvRecord row) {
        return new OrderConsistencyIssue.Position(row.recordNumber(), row.startLine(), row.endLine());
    }
    static void require(boolean condition) { if (!condition) throw invalid(); }
    static ApplicationException invalid() { return new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE, "Snapshot is not eligible for conversion."); }
    static ApplicationException unavailable() { return new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE, "Conversion admission could not be completed."); }
}
