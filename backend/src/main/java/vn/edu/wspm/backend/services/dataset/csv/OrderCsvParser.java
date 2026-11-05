package vn.edu.wspm.backend.services.dataset.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static vn.edu.wspm.backend.services.dataset.csv.CsvStructureException.Code.*;

/**
 * Strict wdm-order-csv-v1 structure reader, independent of storage and business rules.
 * Does not close the caller's stream. Callbacks receive provisional records: only
 * normal return confirms the entire document is structurally readable. Callers must
 * discard/stage callback effects until then. Consumer failures and I/O failures propagate.
 */
public final class OrderCsvParser {
    private static final List<String> HEADER = List.of(
            "order_id", "order_item_id", "customer_code", "ordered_at", "status", "product_id",
            "sku_snapshot", "product_name_snapshot", "quantity", "list_unit_price",
            "allocated_discount_amount", "line_total", "currency_code");

    private final int maxRecordCharacters;

    /** Caller-selected resource budget in UTF-16 units, including CSV syntax and terminators, excluding BOM. */
    public OrderCsvParser(int maxRecordCharacters) {
        if (maxRecordCharacters <= 0) throw new IllegalArgumentException("Record budget must be positive");
        this.maxRecordCharacters = maxRecordCharacters;
    }

    /** Returns data-record count; a header-only document returns zero. */
    public long parse(InputStream input, Consumer<ParsedCsvRecord> consumer) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(consumer, "consumer");
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        var cursor = new Cursor(new BufferedReader(new InputStreamReader(input, decoder)));
        try {
            ParsedCsvRecord header = cursor.record();
            if (header == null) throw cursor.failure(CSV_MISSING_HEADER);
            if (!header.fields().equals(HEADER)) throw new CsvStructureException(
                    CSV_HEADER_MISMATCH, 1, header.startLine(), header.endLine());
            long count = 0;
            ParsedCsvRecord row;
            while ((row = cursor.record()) != null) {
                consumer.accept(row);
                count++;
            }
            return count;
        } catch (CharacterCodingException invalidUtf8) {
            // Decoder read-ahead prevents claiming an exact invalid-byte position.
            throw cursor.failure(CSV_INVALID_UTF8);
        }
    }

    private enum State { START, UNQUOTED, QUOTED, CLOSED }

    private final class Cursor {
        private final Reader reader;
        private long number;
        private long line = 1;
        private long startLine = 1;
        private long lastLine = 1;
        private int characters;
        private boolean previousCr;
        private boolean first = true;

        private Cursor(Reader reader) { this.reader = reader; }

        private int next() throws IOException {
            int ch = reader.read();
            if (first) {
                first = false;
                if (ch == '\uFEFF') {
                    ch = reader.read();
                    if (ch == '\uFEFF') throw failure(CSV_INVALID_BOM);
                }
            }
            if (ch != -1) {
                lastLine = ch == '\n' && previousCr ? line - 1 : line;
                if (characters == maxRecordCharacters) throw failure(CSV_RECORD_LIMIT_EXCEEDED);
                characters++;
                if (ch == '\r') line++;
                else if (ch == '\n' && !previousCr) line++;
                previousCr = ch == '\r';
            }
            return ch;
        }

        private ParsedCsvRecord record() throws IOException {
            number++;
            startLine = line;
            lastLine = line;
            characters = 0;
            var fields = new ArrayList<String>(HEADER.size());
            var field = new StringBuilder();
            State state = State.START;
            boolean touched = false;
            while (true) {
                int ch = next();
                if (ch == -1) {
                    if (state == State.QUOTED) throw failure(CSV_UNCLOSED_QUOTE);
                    if (!touched) return null;
                    return finish(fields, field);
                }
                touched = true;
                if (state == State.QUOTED) {
                    if (ch == '"') state = State.CLOSED;
                    else field.append((char) ch);
                    continue;
                }
                if (ch == ',' || ch == '\r' || ch == '\n') {
                    fields.add(field.toString());
                    field.setLength(0);
                    if (ch == ',') {
                        // A comma after field 13 necessarily starts an extra field.
                        if (fields.size() >= HEADER.size()) throw failure(CSV_COLUMN_COUNT_MISMATCH);
                        state = State.START;
                        continue;
                    }
                    if (ch == '\r' && next() != '\n') throw failure(CSV_INVALID_RECORD_SEPARATOR);
                    return finish(fields);
                }
                if (ch == '"') {
                    if (state == State.START) state = State.QUOTED;
                    else if (state == State.CLOSED) {
                        field.append('"');
                        state = State.QUOTED;
                    } else throw failure(CSV_INVALID_QUOTE);
                } else {
                    if (state == State.CLOSED) throw failure(CSV_INVALID_QUOTE);
                    field.append((char) ch);
                    state = State.UNQUOTED;
                }
            }
        }

        private ParsedCsvRecord finish(List<String> fields, StringBuilder field) {
            fields.add(field.toString());
            return finish(fields);
        }

        private ParsedCsvRecord finish(List<String> fields) {
            if (fields.size() != HEADER.size()) throw failure(CSV_COLUMN_COUNT_MISMATCH);
            return new ParsedCsvRecord(number, startLine, lastLine, fields);
        }

        private CsvStructureException failure(CsvStructureException.Code code) {
            return new CsvStructureException(code, number, startLine, lastLine);
        }
    }
}
