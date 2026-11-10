package vn.edu.wspm.backend.services.combo.admin;

public final class ComboReadException extends RuntimeException {
    public enum Code { INVALID_QUERY, INVALID_PAGE, INVALID_ID, INVALID_COMBO_STATUS, INVALID_COMBO_KIND,
        BATCH_NOT_FOUND, COMBO_NOT_FOUND }
    private final Code code;
    public ComboReadException(Code code) { super(code.name());this.code=code; }
    public Code code() { return code; }
}
