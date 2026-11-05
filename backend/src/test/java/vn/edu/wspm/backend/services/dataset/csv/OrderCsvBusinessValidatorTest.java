package vn.edu.wspm.backend.services.dataset.csv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.wspm.backend.services.dataset.csv.BusinessValidationIssue.Code.*;

class OrderCsvBusinessValidatorTest {
    private final OrderCsvBusinessValidator validator = new OrderCsvBusinessValidator();
    private final Set<String> catalog = Set.of("product-laptop", "product-mouse", "product-bag");

    private ParsedCsvRecord row() {
        return new ParsedCsvRecord(7, 9, 10, List.of("order", "9007199254740993", "anon-A",
                "2026-11-01T01:02:03.123456Z", "COMPLETED", "product-laptop", "=OLD-SKU",
                " Laptop Việt, \"bản cũ\"\r\nDòng hai ", "2", "100.50", "0.50", "200.50", "VND"));
    }

    private ParsedCsvRecord change(ParsedCsvRecord row, int column, String value) {
        var fields = new ArrayList<>(row.fields());
        fields.set(column, value);
        return new ParsedCsvRecord(row.recordNumber(), row.startLine(), row.endLine(), fields);
    }

    private OrderCsvBusinessValidator.Result validate(int column, String value) {
        return validator.validate(change(row(), column, value), catalog);
    }

    private void error(int column, String value, BusinessValidationIssue.Code code) {
        var result = validate(column, value);
        assertFalse(result.valid());
        assertNull(result.row());
        assertEquals(1, result.issues().size());
        assertEquals(code, result.issues().getFirst().code());
        assertEquals(7, result.issues().getFirst().recordNumber());
        assertEquals(9, result.issues().getFirst().startLine());
        assertEquals(10, result.issues().getFirst().endLine());
    }

    @Test
    void validRowPreservesHistoryValuesPrecisionAndSourcePosition() {
        var source = row();
        var result = validator.validate(source, catalog);
        assertTrue(result.valid());
        assertTrue(result.issues().isEmpty());
        assertEquals(new ValidatedOrderRow(7, 9, 10, "order", new BigInteger("9007199254740993"),
                "anon-A", Instant.parse("2026-11-01T01:02:03.123456Z"), "COMPLETED", "product-laptop",
                "=OLD-SKU", " Laptop Việt, \"bản cũ\"\r\nDòng hai ", 2,
                new BigDecimal("100.50"), new BigDecimal("0.50"), new BigDecimal("200.50"), "VND"), result.row());
        assertEquals(source, row());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\r\n", "\u00a0", "\u2007", "\u202f", "\u3000"})
    void allThirteenFieldsAreRequiredIncludingUnicodeSpaces(String blank) {
        for (int column = 0; column < 13; column++) error(column, blank, FIELD_REQUIRED);
    }

    @Test
    void codePointLengthBoundariesDoNotNormalizeOrTrim() {
        int[] columns = {0, 2, 5, 6, 7};
        int[] limits = {40, 80, 40, 80, 200};
        for (int index = 0; index < columns.length; index++) {
            int column = columns[index];
            String exact = "😀".repeat(limits[index]);
            var source = change(row(), column, exact);
            var products = column == 5 ? Set.of(exact) : catalog;
            assertTrue(validator.validate(source, products).valid());
            error(column, exact + "x", FIELD_TOO_LONG);
        }
        assertEquals(" e\u0301 ", validate(0, " e\u0301 ").row().orderId());
        assertTrue(validate(0, "\u200b").valid()); // zero-width space is not in the declared blank predicate
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "+1", "01", "1.0", "1e3", " 1", "1 ", "١", "１"})
    void rejectsNoncanonicalPositiveIntegers(String input) {
        error(1, input, INVALID_INTEGER_FORMAT);
        error(8, input, INVALID_INTEGER_FORMAT);
    }

    @Test
    void unsignedIntegerBoundariesAndLargeInputsAreHandledExactly() {
        assertEquals(BigInteger.ONE, validate(1, "1").row().orderItemId());
        assertEquals(new BigInteger("18446744073709551615"), validate(1, "18446744073709551615").row().orderItemId());
        error(1, "18446744073709551616", VALUE_OUT_OF_RANGE);
        error(1, "9".repeat(10_000), VALUE_OUT_OF_RANGE);
        error(8, "4294967296", VALUE_OUT_OF_RANGE);
        var maximum = change(change(change(row(), 8, "4294967295"), 9, "1.00"), 11, "4294967294.50");
        assertEquals(4294967295L, validator.validate(maximum, catalog).row().quantity());
        var minimum = change(change(row(), 8, "1"), 11, "100.00");
        assertEquals(1, validator.validate(minimum, catalog).row().quantity());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-29T00:00:00.000000Z", "2026-11-01T24:00:00.000000Z",
            "2026-11-01T00:00:60.000000Z", "2026-11-01T00:00:00Z", "2026-11-01T00:00:00.000Z",
            "2026-11-01T00:00:00.0000000Z", "2026-11-01T00:00:00.000000+00:00",
            "2026-11-01T00:00:00.000000z", "2026-11-01 00:00:00.000000Z", "not-a-date"})
    void strictTimestampRejectsNormalizationAndInvalidCalendarValues(String input) {
        error(3, input, INVALID_TIMESTAMP);
    }

    @Test
    void leapDayAndMicrosecondsArePreservedWithoutFutureDateRules() {
        assertEquals(Instant.parse("2028-02-29T23:59:59.999999Z"),
                validate(3, "2028-02-29T23:59:59.999999Z").row().orderedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"completed", "COMPLETED ", "CANCELLED", "RETURNED", "REFUNDED"})
    void completedMustMatchExactly(String input) { error(4, input, STATUS_NOT_COMPLETED); }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1.0", "1.000", "+1.00", "-1.00", "01.00", "1e2", "1,000.00",
            " 1.00", "100000000000000000.00", "NaN", ".50", "١.00"})
    void moneyFormatChecksAllThreeFieldsBeforeArithmetic(String input) {
        error(9, input, INVALID_MONEY_FORMAT);
        error(10, input, INVALID_MONEY_FORMAT);
        error(11, input, INVALID_MONEY_FORMAT);
    }

    @Test
    void zeroAndMaximumMoneyAndGrossBeyondDecimal19AreExact() {
        var zero = change(change(change(row(), 9, "0.00"), 10, "0.00"), 11, "0.00");
        assertTrue(validator.validate(zero, catalog).valid());
        String maximum = "99999999999999999.99";
        var max = change(change(change(row(), 8, "1"), 9, maximum), 10, maximum);
        max = change(max, 11, "0.00");
        assertTrue(validator.validate(max, catalog).valid());
        var largeGross = change(change(row(), 8, "4294967295"), 9, maximum);
        var result = validator.validate(largeGross, catalog);
        assertEquals(List.of(LINE_TOTAL_MISMATCH), result.issues().stream().map(BusinessValidationIssue::code).toList());
    }

    @Test
    void decimalEqualityDoesNotRoundAndDiscountFailureIsReported() {
        assertTrue(validator.validate(row(), catalog).valid());
        error(11, "200.49", LINE_TOTAL_MISMATCH);
        var result = validate(10, "201.01");
        assertEquals(List.of(DISCOUNT_EXCEEDS_GROSS, LINE_TOTAL_MISMATCH),
                result.issues().stream().map(BusinessValidationIssue::code).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"vnd", "VN", "VNDD", "VÑD", "VN1", " VND"})
    void currencyIsThreeUppercaseAsciiLetters(String input) { error(12, input, INVALID_CURRENCY_CODE); }

    @Test
    void multipleCurrenciesArePreservedWithoutWhitelistOrConversion() {
        assertEquals("USD", validate(12, "USD").row().currencyCode());
        assertEquals("ZZZ", validate(12, "ZZZ").row().currencyCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "PRODUCT-LAPTOP", "product-laptop ", "product-láptop"})
    void productReferencesAreExactAndDoNotUseCurrentPriceNameOrStatus(String input) {
        error(5, input, PRODUCT_NOT_FOUND);
    }

    @Test
    void collectsIndependentErrorsInColumnOrderWithoutRawDataOrDependentNoise() {
        var fields = new ArrayList<>(row().fields());
        fields.set(0, "  ");
        fields.set(1, "private-not-a-number");
        fields.set(3, "private-date");
        fields.set(4, "private-status");
        fields.set(5, "private-product");
        fields.set(7, "x".repeat(201));
        fields.set(8, "-1");
        fields.set(9, "NaN");
        fields.set(12, "invalid");
        var source = new ParsedCsvRecord(7, 9, 10, fields);
        var result = validator.validate(source, catalog);
        assertEquals(List.of("order_id", "order_item_id", "ordered_at", "status", "product_id",
                "product_name_snapshot", "quantity", "list_unit_price", "currency_code"),
                result.issues().stream().map(BusinessValidationIssue::field).toList());
        assertEquals(result, validator.validate(source, catalog));
        assertFalse(result.issues().toString().contains("private-"));
        assertThrows(UnsupportedOperationException.class, () -> result.issues().clear());
        assertThrows(IllegalArgumentException.class, () -> new OrderCsvBusinessValidator.Result(null, List.of()));
    }

    @Test
    void rejectsStructuralMisuseRatherThanAssigningBusinessErrors() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(new ParsedCsvRecord(2, 2, 2, List.of("short")), catalog));
    }

    @Test
    void fixturesComposeWithAcceptedParserAndLeaveConflictsAndDuplicatesToNextTask() throws Exception {
        var results = fixture("valid.csv");
        assertEquals(5, results.size());
        assertTrue(results.stream().allMatch(OrderCsvBusinessValidator.Result::valid));
        assertTrue(fixture("empty.csv").isEmpty());
        var invalid = fixture("business-invalid.csv");
        assertEquals(5, invalid.size());
        assertEquals(PRODUCT_NOT_FOUND, invalid.getFirst().issues().getFirst().code());
        assertEquals(4, invalid.stream().filter(OrderCsvBusinessValidator.Result::valid).count());
        assertEquals(invalid.get(3).row().orderItemId(), invalid.get(4).row().orderItemId());
        assertNotEquals(invalid.get(1).row().customerCode(), invalid.get(2).row().customerCode());
    }

    private List<OrderCsvBusinessValidator.Result> fixture(String name) throws Exception {
        var results = new ArrayList<OrderCsvBusinessValidator.Result>();
        try (var input = getClass().getResourceAsStream("/wdm/csv-v1/" + name)) {
            assertNotNull(input);
            new OrderCsvParser(16_384).parse(input, source -> results.add(validator.validate(source, catalog)));
        }
        return results;
    }
}
