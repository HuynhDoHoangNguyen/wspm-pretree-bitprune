package vn.edu.wspm.backend.services.dataset.csv;

/** Safe structural diagnostic: contains no input values or raw records. */
public final class CsvStructureException extends RuntimeException {
    public enum Code {
        CSV_INVALID_UTF8, CSV_INVALID_BOM, CSV_MISSING_HEADER, CSV_HEADER_MISMATCH,
        CSV_UNCLOSED_QUOTE, CSV_INVALID_QUOTE, CSV_INVALID_RECORD_SEPARATOR,
        CSV_COLUMN_COUNT_MISMATCH, CSV_RECORD_LIMIT_EXCEEDED
    }

    private final Code code;
    private final long recordNumber;
    private final long startLine;
    private final long endLine;

    CsvStructureException(Code code, long recordNumber, long startLine, long endLine) {
        super(code.name() + " at record " + recordNumber + ", lines " + startLine + "-" + endLine);
        this.code = code;
        this.recordNumber = recordNumber;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    public Code code() { return code; }
    public long recordNumber() { return recordNumber; }
    public long startLine() { return startLine; }
    public long endLine() { return endLine; }
}
