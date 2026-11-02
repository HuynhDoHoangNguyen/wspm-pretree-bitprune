package vn.edu.wspm.io;

/** Indicates that an input token stream does not follow the supported SPMF sequence format. */
public final class SpmfParseException extends IllegalArgumentException {

    public SpmfParseException(String message) {
        super(message);
    }
}
