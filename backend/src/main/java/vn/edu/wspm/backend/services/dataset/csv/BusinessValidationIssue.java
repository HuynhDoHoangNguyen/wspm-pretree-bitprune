package vn.edu.wspm.backend.services.dataset.csv;

/** Fixed diagnostic metadata; never includes rejected values or raw CSV. */
public record BusinessValidationIssue(Code code, String field, long recordNumber, long startLine, long endLine) {
    public enum Code {
        FIELD_REQUIRED, FIELD_TOO_LONG, INVALID_INTEGER_FORMAT, VALUE_OUT_OF_RANGE,
        STATUS_NOT_COMPLETED, INVALID_TIMESTAMP, INVALID_MONEY_FORMAT,
        DISCOUNT_EXCEEDS_GROSS, LINE_TOTAL_MISMATCH, INVALID_CURRENCY_CODE, PRODUCT_NOT_FOUND
    }
}
