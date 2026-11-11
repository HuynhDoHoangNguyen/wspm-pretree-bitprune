package vn.edu.wspm.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.regex.Pattern;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;

/** Parses FIMI transaction-per-line input without inventing temporal order between items. */
public final class FimiTransactionParser {

    private static final Pattern INTEGER_TOKEN = Pattern.compile("[+-]?\\d+");

    /** Parses UTF-8 input and closes the reader opened by this method. */
    public SequenceDatabase parse(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        }
    }

    /**
     * Parses input without closing the caller-owned reader.
     *
     * <p>Each non-blank line becomes one sequence containing exactly one itemset. Blank lines are
     * ignored, and one-based SIDs are assigned consecutively in non-blank line order. Duplicate
     * items are rejected rather than silently collapsed.</p>
     */
    public SequenceDatabase parse(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader must not be null");
        var bufferedReader = reader instanceof BufferedReader existing
                ? existing
                : new BufferedReader(reader);
        var records = new ArrayList<SequenceRecord>();

        String line;
        int lineNumber = 0;
        while ((line = bufferedReader.readLine()) != null) {
            lineNumber++;
            String strippedLine = line.strip();
            if (strippedLine.isEmpty()) {
                continue;
            }

            var items = new ArrayList<Item>();
            var itemIds = new HashSet<Integer>();
            String[] tokens = strippedLine.split("\\s+");
            for (int tokenIndex = 0; tokenIndex < tokens.length; tokenIndex++) {
                int itemId = parseItemId(tokens[tokenIndex], lineNumber, tokenIndex + 1);
                if (!itemIds.add(itemId)) {
                    throw malformed(lineNumber, tokenIndex + 1,
                            "duplicate item in transaction: " + itemId);
                }
                items.add(new Item(itemId));
            }

            int sid = records.size() + 1;
            records.add(new SequenceRecord(
                    sid, Sequence.of(new Itemset(items))));
        }
        return new SequenceDatabase(records);
    }

    private static int parseItemId(String token, int lineNumber, int tokenIndex) {
        final int value;
        try {
            value = Integer.parseInt(token);
        } catch (NumberFormatException error) {
            if (INTEGER_TOKEN.matcher(token).matches()) {
                throw malformed(lineNumber, tokenIndex,
                        "integer token is outside the supported int range: " + token);
            }
            throw malformed(lineNumber, tokenIndex,
                    "token is not a valid integer: " + token);
        }
        if (value < 0) {
            throw malformed(lineNumber, tokenIndex,
                    "negative item id and SPMF delimiters are not allowed: " + value);
        }
        return value;
    }

    private static FimiParseException malformed(
            int lineNumber, int tokenIndex, String detail) {
        return new FimiParseException(
                "malformed FIMI input at line " + lineNumber
                        + ", token " + tokenIndex + ": " + detail);
    }
}
