package vn.edu.wspm.backend.services.pattern;

/** Internal error contract. IMP-01 must translate codes after server authorization. */
public final class PatternReadException extends RuntimeException {
    public enum Code { INVALID_PAGE, INVALID_RUN_ID, INVALID_PATTERN_ID, RUN_NOT_FOUND,
        RESULT_INCONSISTENT, RESULTS_NOT_AVAILABLE, PATTERN_NOT_FOUND,
        INVALID_FILTER, INVALID_QUERY, ITEM_NOT_IN_RUN_DICTIONARY, WEIGHT_FILTER_UNAVAILABLE }
    private final Code code;
    private final java.util.List<Integer> offendingItemIds;

    public PatternReadException(Code code) {
        this(code,java.util.List.of());
    }

    public PatternReadException(Code code,java.util.List<Integer> offendingItemIds) {
        super(code.name());
        this.code = code;
        this.offendingItemIds=java.util.List.copyOf(offendingItemIds);
    }

    public Code getCode() { return code; }
    public java.util.List<Integer> getOffendingItemIds() { return offendingItemIds; }
}
