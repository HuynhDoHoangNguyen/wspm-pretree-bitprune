package vn.edu.wspm.backend.services.dataset.csv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.wspm.backend.repositories.export.OrderExportRow;
import vn.edu.wspm.backend.services.export.OrderCsvWriter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.wspm.backend.services.dataset.csv.CsvStructureException.Code.*;

class OrderCsvParserTest {
    private static final String HEADER = "order_id,order_item_id,customer_code,ordered_at,status,product_id,"
            + "sku_snapshot,product_name_snapshot,quantity,list_unit_price,allocated_discount_amount,line_total,currency_code";
    private final OrderCsvParser parser = new OrderCsvParser(16_384);

    private byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }

    private List<ParsedCsvRecord> parse(String text) throws IOException {
        var records = new ArrayList<ParsedCsvRecord>();
        long count = parser.parse(new ByteArrayInputStream(bytes(text)), records::add);
        assertEquals(records.size(), count);
        return records;
    }

    private String row(String first) { return first + ",b,c,d,e,f,g,h,i,j,k,l,m"; }

    private CsvStructureException error(CsvStructureException.Code code, String text) {
        var failure = assertThrows(CsvStructureException.class, () -> parse(text));
        assertEquals(code, failure.code());
        return failure;
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\n", "\r\n"})
    void headerOnlyAndOptionalFinalTerminator(String terminator) throws Exception {
        assertTrue(parse(HEADER + terminator).isEmpty());
        assertTrue(parse("\uFEFF" + HEADER + terminator).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n"})
    void preservesMultilineUnicodeAndReportsPhysicalLines(String newline) throws Exception {
        String first = "\" Laptop Việt 😀, \"\"bản cũ\"\"" + newline + "Dòng hai \"";
        var records = parse("\uFEFF" + HEADER + newline + row(first) + newline + row("second") + newline);
        assertEquals(" Laptop Việt 😀, \"bản cũ\"" + newline + "Dòng hai ", records.getFirst().fields().getFirst());
        assertEquals(new ParsedCsvRecord(2, 2, 3, records.getFirst().fields()), records.getFirst());
        assertEquals(new ParsedCsvRecord(3, 4, 4, records.get(1).fields()), records.get(1));
    }

    @Test
    void mixedSeparatorsAndQuotedBareCrArePreserved() throws Exception {
        var records = parse(HEADER + "\r\n" + row("\"a\rb\"") + "\n" + row("c") + "\r\n");
        assertEquals("a\rb", records.getFirst().fields().getFirst());
        assertEquals(3, records.getFirst().endLine());
        assertEquals(4, records.get(1).startLine());
    }

    @Test
    void acceptsQuotedHeaderNamesAfterCsvDecoding() throws Exception {
        assertTrue(parse("\"" + HEADER.replace(",", "\",\"") + "\"").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\uFEFF"})
    void missingHeader(String text) { error(CSV_MISSING_HEADER, text); }

    @ParameterizedTest
    @ValueSource(strings = {"ORDER_ID", "order_id ", "order_item_id", "different"})
    void exactHeaderNamesAndOrder(String first) {
        error(CSV_HEADER_MISMATCH, first + HEADER.substring("order_id".length()));
    }

    @Test
    void rejectsMissingExtraAndReorderedHeaderColumns() {
        error(CSV_COLUMN_COUNT_MISMATCH, HEADER.substring(HEADER.indexOf(',') + 1));
        error(CSV_COLUMN_COUNT_MISMATCH, HEADER + ",extra");
        error(CSV_HEADER_MISMATCH, HEADER.replace("order_id,order_item_id", "order_item_id,order_id"));
        error(CSV_HEADER_MISMATCH, HEADER.replace("order_item_id", "order_id"));
    }

    @Test
    void rejectsDuplicateBomButDoesNotStripBomInsideData() throws Exception {
        error(CSV_INVALID_BOM, "\uFEFF\uFEFF" + HEADER);
        assertEquals("\uFEFFtext", parse(HEADER + "\n" + row("\uFEFFtext")).getFirst().fields().getFirst());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a,b", "a,b,c,d,e,f,g,h,i,j,k,l,m,n", "", ","})
    void rejectsIncorrectRecordWidthIncludingBlankLines(String data) {
        var failure = error(CSV_COLUMN_COUNT_MISMATCH, HEADER + "\n" + data + "\n");
        assertEquals(2, failure.recordNumber());
        assertEquals(2, failure.startLine());
        assertEquals(2, failure.endLine());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\"b", "\"a\"x", "\"a\" ", " \"a\""})
    void rejectsInvalidQuotePlacement(String field) {
        error(CSV_INVALID_QUOTE, HEADER + "\n" + row(field));
    }

    @Test
    void rejectsUnclosedMultilineQuoteWithRange() {
        var failure = error(CSV_UNCLOSED_QUOTE, HEADER + "\r\n\"a\r\nb");
        assertEquals(2, failure.recordNumber());
        assertEquals(2, failure.startLine());
        assertEquals(3, failure.endLine());
    }

    @Test
    void quotedEmptyAndSingleQuoteFieldsRespectClosingQuoteAtEof() throws Exception {
        var records = parse(HEADER + "\n" + row("\"\"\"\"") + "\n"
                + ",,,,,,,,,,,,\"\"");
        assertEquals("\"", records.getFirst().fields().getFirst());
        assertEquals(List.of("", "", "", "", "", "", "", "", "", "", "", "", ""), records.get(1).fields());
        error(CSV_UNCLOSED_QUOTE, HEADER + "\n\"\"\"");
    }

    @Test
    void handlesUtf8BomAndMultilineQuotesAcrossOneByteReads() throws Exception {
        byte[] content = bytes("\uFEFF" + HEADER + "\r\n" + row("\"Việt 😀\r\n\"\"quote\"\"\""));
        var input = new ByteArrayInputStream(content) {
            @Override public synchronized int read(byte[] buffer, int offset, int length) {
                return super.read(buffer, offset, Math.min(length, 1));
            }
        };
        var records = new ArrayList<ParsedCsvRecord>();
        assertEquals(1, parser.parse(input, records::add));
        assertEquals("Việt 😀\r\n\"quote\"", records.getFirst().fields().getFirst());
        assertEquals(2, records.getFirst().startLine());
        assertEquals(3, records.getFirst().endLine());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\r", "\rx"})
    void rejectsBareCrOutsideQuotes(String separator) {
        error(CSV_INVALID_RECORD_SEPARATOR, HEADER + separator);
    }

    @Test
    void acceptsEmptyFieldsAndDefersAllBusinessChecks() throws Exception {
        String data = ",\"\",customer,not-a-time,CANCELLED,missing,=SKU,+name,NaN,-1,1e9,wrong,";
        var fields = parse(HEADER + "\n" + data).getFirst().fields();
        assertEquals(List.of("", "", "customer", "not-a-time", "CANCELLED", "missing", "=SKU", "+name",
                "NaN", "-1", "1e9", "wrong", ""), fields);
    }

    @Test
    void preservesStringsWithoutNormalizationAndReturnsImmutableFields() throws Exception {
        String text = "  =+@-e\u0301😀  ";
        var record = parse(HEADER + "\n" + row(text)).getFirst();
        assertEquals(text, record.fields().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> record.fields().set(0, "x"));
        var source = new ArrayList<>(List.of("original"));
        var copy = new ParsedCsvRecord(2, 2, 2, source);
        source.set(0, "changed");
        assertEquals("original", copy.fields().getFirst());
    }

    @Test
    void readsMaintainedFixturesWithoutBusinessFiltering() throws Exception {
        var valid = fixture("valid.csv");
        assertEquals(5, valid.size());
        assertEquals("Laptop Việt, \"bản cũ\"\r\nDòng thứ hai", valid.getFirst().fields().get(7));
        assertEquals("=OLD-SKU", valid.getFirst().fields().get(6));
        assertEquals("9007199254740993", valid.get(2).fields().get(1));
        assertEquals(List.of("9", "10", "9007199254740993", "9007199254740994", "9007199254740995"),
                valid.stream().map(record -> record.fields().get(1)).toList());
        assertTrue(fixture("empty.csv").isEmpty());
        var invalidBusiness = fixture("business-invalid.csv");
        assertEquals(5, invalidBusiness.size());
        assertEquals("product-missing", invalidBusiness.getFirst().fields().get(5));
        assertEquals(invalidBusiness.get(3).fields(), invalidBusiness.get(4).fields());
    }

    private List<ParsedCsvRecord> fixture(String name) throws IOException {
        var records = new ArrayList<ParsedCsvRecord>();
        try (var input = getClass().getResourceAsStream("/wdm/csv-v1/" + name)) {
            assertNotNull(input);
            assertEquals(parser.parse(input, records::add), records.size());
        }
        return records;
    }

    @Test
    void writerRoundTripPreservesEveryFieldAndDoesNotModifyInputBytes() throws Exception {
        var original = new OrderExportRow("order-😀", new BigInteger("18446744073709551615"), "anon-A",
                Instant.parse("2026-11-01T01:02:03.123456Z"), "COMPLETED", "product", "=SKU",
                "Laptop Việt, \"bản cũ\"\r\nDòng hai", 4294967295L,
                new BigDecimal("100.00"), new BigDecimal("0.50"), new BigDecimal("199.50"), "VND");
        byte[] input = new OrderCsvWriter().serialize(List.of(original), 4096);
        byte[] before = input.clone();
        var rows = new ArrayList<ParsedCsvRecord>();
        assertEquals(1, parser.parse(new ByteArrayInputStream(input), rows::add));
        assertEquals(List.of("order-😀", "18446744073709551615", "anon-A", "2026-11-01T01:02:03.123456Z",
                "COMPLETED", "product", "=SKU", "Laptop Việt, \"bản cũ\"\r\nDòng hai", "4294967295",
                "100.00", "0.50", "199.50", "VND"), rows.getFirst().fields());
        assertArrayEquals(before, input);
    }

    @Test
    void strictUtf8RejectsInvalidTruncatedOverlongSurrogateAndUtf16Input() {
        List<byte[]> invalid = List.of(new byte[]{(byte) 0xff}, new byte[]{(byte) 0xe2, (byte) 0x82},
                new byte[]{(byte) 0xc0, (byte) 0xaf}, new byte[]{(byte) 0xed, (byte) 0xa0, (byte) 0x80},
                new byte[]{(byte) 0xff, (byte) 0xfe, 65, 0});
        for (byte[] sequence : invalid) {
            byte[] prefix = bytes(HEADER + "\n" + row("safe") + "\n");
            byte[] input = Arrays.copyOf(prefix, prefix.length + sequence.length);
            System.arraycopy(sequence, 0, input, prefix.length, sequence.length);
            var failure = assertThrows(CsvStructureException.class,
                    () -> parser.parse(new ByteArrayInputStream(input), ignored -> {}));
            assertEquals(CSV_INVALID_UTF8, failure.code());
            assertNull(failure.getCause());
            assertFalse(failure.getMessage().contains("safe"));
        }
    }

    @Test
    void boundsRecordMemoryAndCountsSyntaxTerminatorsAndUtf16Units() throws Exception {
        String data = row("\"" + "😀".repeat(100) + "\"") + "\r\n";
        int budget = data.length();
        assertTrue(budget > HEADER.length() + 2);
        var exact = new OrderCsvParser(budget);
        assertEquals(1, exact.parse(new ByteArrayInputStream(bytes(HEADER + "\n" + data)), ignored -> {}));
        var smaller = new OrderCsvParser(budget - 1);
        var failure = assertThrows(CsvStructureException.class,
                () -> smaller.parse(new ByteArrayInputStream(bytes(HEADER + "\n" + data)), ignored -> {}));
        assertEquals(CSV_RECORD_LIMIT_EXCEEDED, failure.code());
        assertEquals(2, failure.recordNumber());
        assertThrows(IllegalArgumentException.class, () -> new OrderCsvParser(0));
        assertThrows(IllegalArgumentException.class, () -> new OrderCsvParser(-1));
        assertEquals(0, new OrderCsvParser(HEADER.length()).parse(
                new ByteArrayInputStream(bytes("\uFEFF" + HEADER)), ignored -> {}));
    }

    @Test
    void failFastLeavesOnlyProvisionalPrefixAndDiagnosticsDoNotExposeFields() {
        var rows = new ArrayList<ParsedCsvRecord>();
        var failure = assertThrows(CsvStructureException.class, () -> parser.parse(
                new ByteArrayInputStream(bytes(HEADER + "\n" + row("good") + "\nsecret-private\n" + row("later"))), rows::add));
        assertEquals(1, rows.size());
        assertEquals(3, failure.recordNumber());
        assertEquals(3, failure.startLine());
        assertFalse(failure.toString().contains("secret-private"));
    }

    @Test
    void leavesStreamOwnershipWithCallerAndPropagatesIoAndConsumerFailures() throws Exception {
        var input = new ByteArrayInputStream(bytes(HEADER)) {
            @Override public void close() { fail("Parser must not close caller stream"); }
        };
        parser.parse(input, ignored -> {});
        var io = new IOException("synthetic");
        assertSame(io, assertThrows(IOException.class, () -> parser.parse(new InputStream() {
            @Override public int read() throws IOException { throw io; }
        }, ignored -> {})));
        var consumerFailure = new IllegalStateException("consumer");
        assertSame(consumerFailure, assertThrows(IllegalStateException.class, () -> parser.parse(
                new ByteArrayInputStream(bytes(HEADER + "\n" + row("a"))), ignored -> { throw consumerFailure; })));
    }
}
