package vn.edu.wspm.backend.services.dataset.csv;

import java.util.List;

/** Decoded CSV fields, without business validation. Record 1 is the header. */
public record ParsedCsvRecord(long recordNumber, long startLine, long endLine, List<String> fields) {
    public ParsedCsvRecord {
        fields = List.copyOf(fields);
    }
}
