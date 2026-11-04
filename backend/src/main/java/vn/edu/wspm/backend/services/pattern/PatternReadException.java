package vn.edu.wspm.backend.services.pattern;

/** Internal error contract. IMP-01 must translate codes after server authorization. */
public final class PatternReadException extends RuntimeException {
    public enum Code { INVALID_PAGE, INVALID_RUN_ID, INVALID_PATTERN_ID, RUN_NOT_FOUND,
        RESULT_INCONSISTENT, RESULTS_NOT_AVAILABLE, PATTERN_NOT_FOUND }
    private final Code code;

    public PatternReadException(Code code) {
        super(code.name());
        this.code = code;
    }

    public Code getCode() { return code; }
}
