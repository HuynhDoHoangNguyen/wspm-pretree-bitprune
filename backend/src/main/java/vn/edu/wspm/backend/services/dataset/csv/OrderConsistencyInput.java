package vn.edu.wspm.backend.services.dataset.csv;

import java.util.Objects;

/** Internal handoff: the caller must pair the original source with its own 02.2 result. */
public record OrderConsistencyInput(ParsedCsvRecord source, OrderCsvBusinessValidator.Result business) {
    public OrderConsistencyInput {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(business, "business");
        if (source.fields().size() != 13 || source.recordNumber() < 2
                || source.startLine() < 1 || source.endLine() < source.startLine()) {
            throw new IllegalArgumentException("Consistency requires a parsed data record and source position");
        }
        if (business.valid()) {
            var row = business.row();
            if (row.recordNumber() != source.recordNumber() || row.startLine() != source.startLine()
                    || row.endLine() != source.endLine()) throw mismatchedPosition();
        } else {
            for (var issue : business.issues()) {
                if (issue.recordNumber() != source.recordNumber() || issue.startLine() != source.startLine()
                        || issue.endLine() != source.endLine()) throw mismatchedPosition();
            }
        }
    }

    private static IllegalArgumentException mismatchedPosition() {
        return new IllegalArgumentException("Business result does not match source position");
    }

    /** Absent field errors are proof only when business is the matching, trusted 02.2 result. */
    public boolean usable(String field) {
        return business.issues().stream().noneMatch(issue -> issue.field().equals(field));
    }

    @Override public String toString() {
        return "OrderConsistencyInput[recordNumber=" + source.recordNumber() + ", businessValid=" + business.valid() + "]";
    }
}
