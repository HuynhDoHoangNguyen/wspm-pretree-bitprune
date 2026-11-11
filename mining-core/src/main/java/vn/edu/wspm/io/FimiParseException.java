package vn.edu.wspm.io;

/** Indicates that an input line does not follow the supported FIMI transaction format. */
public final class FimiParseException extends IllegalArgumentException {

    public FimiParseException(String message) {
        super(message);
    }
}
