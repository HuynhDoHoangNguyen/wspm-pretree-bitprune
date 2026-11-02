package vn.edu.wspm.io;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Objects;
import vn.edu.wspm.model.SequenceDatabase;

/** Minimal explicit-format entry point over the independent SPMF and FIMI parsers. */
public final class SequenceDatabaseParser {

    private final SpmfSequenceParser spmfParser = new SpmfSequenceParser();
    private final FimiTransactionParser fimiParser = new FimiTransactionParser();

    public SequenceDatabase parse(Path path, SequenceDatabaseFormat format) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(format, "format must not be null");
        return switch (format) {
            case SPMF_SEQUENTIAL -> spmfParser.parse(path);
            case FIMI_TRANSACTION -> fimiParser.parse(path);
        };
    }

    public SequenceDatabase parse(Reader reader, SequenceDatabaseFormat format) throws IOException {
        Objects.requireNonNull(reader, "reader must not be null");
        Objects.requireNonNull(format, "format must not be null");
        return switch (format) {
            case SPMF_SEQUENTIAL -> spmfParser.parse(reader);
            case FIMI_TRANSACTION -> fimiParser.parse(reader);
        };
    }
}
