package vn.edu.wspm.backend.services.dataset.validation;

import org.springframework.stereotype.Component;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.services.dataset.csv.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/** Orchestrates the accepted pure stages; caller owns integrity verification and publication. */
@Component
public class ValidationReportEngine {
    private final ProductReferenceLookup catalog;
    private final ValidationLimits limits;
    private final Clock clock;

    public ValidationReportEngine(ProductReferenceLookup catalog, ValidationLimits limits, Clock clock) {
        this.catalog = catalog;
        this.limits = limits;
        this.clock = clock;
    }

    public ValidationReport generate(String id, String sha, String size, byte[] raw, Runnable budget) throws IOException {
        var rows = new ArrayList<ParsedCsvRecord>();
        var diagnostics = new ArrayList<ValidationReport.Diagnostic>();
        budget.run();
        if (raw.length > limits.maxRawBytes()) throw unavailable();
        try {
            new OrderCsvParser(limits.maxRecordCharacters()).parse(new ByteArrayInputStream(raw), row -> {
                budget.run();
                if (rows.size() >= limits.maxRows()) throw unavailable();
                rows.add(row);
            });
        } catch (CsvStructureException structural) {
            if (structural.code() == CsvStructureException.Code.CSV_RECORD_LIMIT_EXCEEDED) throw unavailable();
            diagnostics.add(new ValidationReport.Diagnostic("STRUCTURE", structural.code().name(), null,
                    structural.code() == CsvStructureException.Code.CSV_INVALID_UTF8 ? "DECODER_CURSOR" : "RECORD_RANGE",
                    List.of(new OrderConsistencyIssue.Position(structural.recordNumber(), structural.startLine(), structural.endLine()))));
            var counts = new ValidationReport.Counts(Integer.toString(rows.size()), null, null, null,
                    null, null, null, null, null, null, null, "1");
            return report(id, sha, size, "INVALID", "NOT_RUN_STRUCTURAL_ERROR", false, counts, diagnostics, List.of());
        }
        budget.run();
        var requested = new LinkedHashSet<String>();
        for (var row : rows) {
            String product = row.fields().get(5);
            // A field over CSV-v1's limit cannot be a valid catalog reference; validator owns its error.
            if (!product.isEmpty() && product.codePointCount(0, product.length()) <= 40) requested.add(product);
        }
        var existing = requested.isEmpty() ? java.util.Set.<String>of() : catalog.findExisting(requested);
        budget.run();
        var validator = new OrderCsvBusinessValidator();
        var inputs = new ArrayList<OrderConsistencyInput>();
        var knownOrders = new LinkedHashSet<String>();
        long invalid = 0;
        boolean unknownOrder = false;
        for (var row : rows) {
            budget.run();
            var business = validator.validate(row, existing);
            var input = new OrderConsistencyInput(row, business);
            inputs.add(input);
            if (input.usable("order_id")) knownOrders.add(row.fields().getFirst());
            else unknownOrder = true;
            if (!business.valid()) invalid++;
            for (var issue : business.issues()) diagnostics.add(new ValidationReport.Diagnostic("BUSINESS",
                    issue.code().name(), issue.field(), "RECORD_RANGE", List.of(new OrderConsistencyIssue.Position(
                            issue.recordNumber(), issue.startLine(), issue.endLine()))));
        }
        var consistency = new OrderConsistencyChecker().check(inputs);
        budget.run();
        for (var issue : consistency.issues()) diagnostics.add(new ValidationReport.Diagnostic("CONSISTENCY",
                issue.code().name(), issue.field(), "RECORD_RANGE", issue.positions()));
        var cleaned = new OrderCleaningPipeline().clean(consistency);
        budget.run();
        if (!cleaned.hasUsableOrders()) diagnostics.add(new ValidationReport.Diagnostic("CLEANING",
                cleaned.outcome().name(), null, "RECORD_RANGE", List.of()));
        diagnostics.sort(Comparator.comparingLong((ValidationReport.Diagnostic issue) -> issue.positions().isEmpty()
                        ? Long.MAX_VALUE : issue.positions().getFirst().recordNumber())
                .thenComparing(ValidationReport.Diagnostic::stage).thenComparing(ValidationReport.Diagnostic::code)
                .thenComparing(issue -> issue.field() == null ? "" : issue.field()));
        var manifest = cleaned.dispositions().stream().map(row -> new ValidationReport.Decision(row.position(),
                row.action().name(), row.representative(), row.reasons().stream().map(Enum::name).toList())).toList();
        var counts = new ValidationReport.Counts(Integer.toString(rows.size()), Integer.toString(rows.size()),
                unknownOrder ? null : Integer.toString(knownOrders.size()), Integer.toString(knownOrders.size()),
                Long.toString(invalid), Long.toString(cleaned.count(OrderCleaningResult.Action.KEPT)),
                Long.toString(cleaned.count(OrderCleaningResult.Action.REJECTED_ORDER)),
                Long.toString(cleaned.count(OrderCleaningResult.Action.DEDUPLICATED)),
                Long.toString(cleaned.count(OrderCleaningResult.Action.BLOCKED)), Integer.toString(cleaned.orders().size()),
                cleaned.outcome() == OrderCleaningResult.Outcome.BLOCKED_UNATTRIBUTABLE_ORDER
                        ? null : Integer.toString(cleaned.rejectedOrders().size()), Integer.toString(diagnostics.size()));
        return report(id, sha, size, cleaned.hasUsableOrders() ? "VALID" : "INVALID", cleaned.outcome().name(),
                true, counts, diagnostics, manifest);
    }

    private ValidationReport report(String id, String sha, String size, String status, String outcome, boolean complete,
            ValidationReport.Counts counts, List<ValidationReport.Diagnostic> diagnostics, List<ValidationReport.Decision> manifest) {
        return new ValidationReport(ValidationReport.VERSION, "wdm-order-csv-v1", OrderCleaningPipeline.POLICY_VERSION,
                id, sha, size, clock.instant().truncatedTo(ChronoUnit.MICROS).toString(), status, outcome, complete,
                counts, diagnostics, manifest);
    }

    static ApplicationException unavailable() {
        return new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE, "Validation exceeded its processing budget; read its status before retrying.");
    }
}
