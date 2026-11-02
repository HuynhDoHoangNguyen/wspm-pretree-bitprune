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

/** Parses a whitespace-delimited SPMF sequence database. */
public final class SpmfSequenceParser {

    private static final int ITEMSET_DELIMITER = -1;
    private static final int SEQUENCE_DELIMITER = -2;
    private static final Pattern INTEGER_TOKEN = Pattern.compile("[+-]?\\d+");

    /**
     * Parses UTF-8 input from {@code path} and closes the reader opened by this
     * method.
     *
     * @throws IOException        if the input cannot be read
     * @throws SpmfParseException if the token stream is malformed
     */
    public SequenceDatabase parse(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        }
    }

    /**
     * Parses input without closing the caller-owned {@code reader}.
     *
     * <p>
     * SPMF {@code -1} ends a non-empty itemset and {@code -2} ends a non-empty
     * sequence.
     * B3 implementation policies assign one-based SIDs in input order and interpret
     * empty input as
     * an empty database.
     * </p>
     *
     * @throws IOException        if the input cannot be read
     * @throws SpmfParseException if the token stream is malformed
     */
    public SequenceDatabase parse(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader must not be null");

        var records = new ArrayList<SequenceRecord>();
        var sequenceItemsets = new ArrayList<Itemset>();
        var itemsetItems = new ArrayList<Item>();
        var itemIds = new HashSet<Integer>();
        var bufferedReader = reader instanceof BufferedReader existing
                ? existing
                : new BufferedReader(reader);

        String line;
        int lineNumber = 0;
        long tokenNumber = 0;
        while ((line = bufferedReader.readLine()) != null) {
            lineNumber++;
            var strippedLine = line.strip();
            if (strippedLine.isEmpty()) {
                continue;
            }

            for (var token : strippedLine.split("\\s+")) {
                tokenNumber++;
                int value = parseInteger(token, tokenNumber, lineNumber);

                if (value == ITEMSET_DELIMITER) {
                    if (itemsetItems.isEmpty()) {
                        throw malformed(tokenNumber, lineNumber,
                                "-1 cannot close an empty itemset");
                    }
                    sequenceItemsets.add(new Itemset(itemsetItems));
                    itemsetItems.clear();
                    itemIds.clear();
                } else if (value == SEQUENCE_DELIMITER) {
                    if (!itemsetItems.isEmpty()) {
                        throw malformed(tokenNumber, lineNumber,
                                "missing -1 before sequence delimiter -2");
                    }
                    if (sequenceItemsets.isEmpty()) {
                        throw malformed(tokenNumber, lineNumber,
                                "-2 cannot close an empty sequence");
                    }
                    int sid = records.size() + 1;
                    records.add(new SequenceRecord(sid, new Sequence(sequenceItemsets)));
                    sequenceItemsets.clear();
                } else if (value < 0) {
                    throw malformed(tokenNumber, lineNumber,
                            "negative item id is not allowed: " + value);
                } else {
                    if (!itemIds.add(value)) {
                        throw malformed(tokenNumber, lineNumber,
                                "duplicate item in itemset: " + value);
                    }
                    itemsetItems.add(new Item(value));
                }
            }
        }

        if (!itemsetItems.isEmpty()) {
            throw new SpmfParseException(
                    "unexpected end of input: missing itemset delimiter -1 and sequence delimiter -2");
        }
        if (!sequenceItemsets.isEmpty()) {
            throw new SpmfParseException(
                    "unexpected end of input: missing sequence delimiter -2");
        }
        return new SequenceDatabase(records);
    }

    private static int parseInteger(String token, long tokenNumber, int lineNumber) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException error) {
            if (INTEGER_TOKEN.matcher(token).matches()) {
                throw malformed(tokenNumber, lineNumber,
                        "integer token is outside the supported int range: " + token);
            }
            throw malformed(tokenNumber, lineNumber,
                    "token is not a valid integer: " + token);
        }
    }

    private static SpmfParseException malformed(long tokenNumber, int lineNumber, String detail) {
        return new SpmfParseException(
                "malformed SPMF input at token " + tokenNumber + ", line " + lineNumber + ": " + detail);
    }
}
