package vn.edu.wspm.backend.services.dataset.csv;

import java.util.List;
import java.util.Objects;

/** Public-safe diagnostic metadata, without order IDs, field values or raw rows. */
public record OrderConsistencyIssue(Code code, String field, List<Position> positions) {
    public enum Code {
        ORDER_FIELD_CONFLICT, DUPLICATE_LINE_ID, LINE_ID_CONTENT_CONFLICT, LINE_ID_ORDER_CONFLICT
    }

    public record Position(long recordNumber, long startLine, long endLine) {}

    public OrderConsistencyIssue {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(field, "field");
        positions = List.copyOf(positions);
    }
}
