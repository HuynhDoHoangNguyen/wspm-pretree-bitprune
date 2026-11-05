package vn.edu.wspm.backend.services.dataset.csv;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static vn.edu.wspm.backend.services.dataset.csv.BusinessValidationIssue.Code.*;

/** Pure row validation. The supplied catalog must contain exact, verified product IDs. */
public final class OrderCsvBusinessValidator {
    private static final List<String> COLUMNS = List.of(
            "order_id", "order_item_id", "customer_code", "ordered_at", "status", "product_id",
            "sku_snapshot", "product_name_snapshot", "quantity", "list_unit_price",
            "allocated_discount_amount", "line_total", "currency_code");
    private static final int[] LENGTHS = {40, 0, 80, 0, 0, 40, 80, 200, 0, 0, 0, 0, 0};
    private static final BigInteger MAX_LINE_ID = new BigInteger("18446744073709551615");
    private static final BigInteger MAX_QUANTITY = new BigInteger("4294967295");
    private static final Pattern INTEGER = Pattern.compile("[1-9][0-9]*");
    private static final Pattern MONEY = Pattern.compile("(0|[1-9][0-9]{0,16})\\.[0-9]{2}");
    private static final Pattern TIMESTAMP = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{6}Z");
    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'", java.util.Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    public record Result(ValidatedOrderRow row, List<BusinessValidationIssue> issues) {
        public Result {
            issues = List.copyOf(issues);
            if ((row == null) == issues.isEmpty()) {
                throw new IllegalArgumentException("Result must contain a valid row or nonempty issues");
            }
        }
        public boolean valid() { return row != null; }
    }

    public Result validate(ParsedCsvRecord source, Set<String> existingProductIds) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(existingProductIds, "existingProductIds");
        if (source.fields().size() != COLUMNS.size()) {
            throw new IllegalArgumentException("Business validation requires a structurally parsed 13-field record");
        }
        List<String> fields = source.fields();
        var errors = new ArrayList<BusinessValidationIssue>();
        boolean[] usable = new boolean[COLUMNS.size()];
        for (int column = 0; column < COLUMNS.size(); column++) {
            String value = fields.get(column);
            if (value.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp))) {
                add(errors, source, column, FIELD_REQUIRED);
            } else if (LENGTHS[column] != 0 && value.codePointCount(0, value.length()) > LENGTHS[column]) {
                add(errors, source, column, FIELD_TOO_LONG);
            } else usable[column] = true;
        }

        BigInteger lineId = usable[1] ? integer(fields.get(1), MAX_LINE_ID, errors, source, 1) : null;
        Instant orderedAt = null;
        if (usable[3]) {
            if (TIMESTAMP.matcher(fields.get(3)).matches()) {
                try {
                    orderedAt = LocalDateTime.parse(fields.get(3), UTC_TIME).toInstant(ZoneOffset.UTC);
                } catch (DateTimeException invalidDate) {
                    add(errors, source, 3, INVALID_TIMESTAMP);
                }
            } else add(errors, source, 3, INVALID_TIMESTAMP);
        }
        if (usable[4] && !"COMPLETED".equals(fields.get(4))) add(errors, source, 4, STATUS_NOT_COMPLETED);
        if (usable[5] && !existingProductIds.contains(fields.get(5))) add(errors, source, 5, PRODUCT_NOT_FOUND);
        BigInteger quantity = usable[8] ? integer(fields.get(8), MAX_QUANTITY, errors, source, 8) : null;
        BigDecimal price = usable[9] ? money(fields.get(9), errors, source, 9) : null;
        BigDecimal discount = usable[10] ? money(fields.get(10), errors, source, 10) : null;
        BigDecimal total = usable[11] ? money(fields.get(11), errors, source, 11) : null;
        if (quantity != null && price != null && discount != null && total != null) {
            BigDecimal gross = price.multiply(new BigDecimal(quantity));
            if (discount.compareTo(gross) > 0) add(errors, source, 10, DISCOUNT_EXCEEDS_GROSS);
            if (total.compareTo(gross.subtract(discount)) != 0) add(errors, source, 11, LINE_TOTAL_MISMATCH);
        }
        if (usable[12] && !CURRENCY.matcher(fields.get(12)).matches()) add(errors, source, 12, INVALID_CURRENCY_CODE);

        // Column order is independent of validation passes; sort is stable for errors on the same field.
        errors.sort(java.util.Comparator.comparingInt(error -> COLUMNS.indexOf(error.field())));
        if (!errors.isEmpty()) return new Result(null, errors);
        return new Result(new ValidatedOrderRow(source.recordNumber(), source.startLine(), source.endLine(),
                fields.get(0), lineId, fields.get(2), orderedAt, fields.get(4), fields.get(5), fields.get(6),
                fields.get(7), quantity.longValueExact(), price, discount, total, fields.get(12)), List.of());
    }

    private BigInteger integer(String value, BigInteger maximum, List<BusinessValidationIssue> errors,
                               ParsedCsvRecord source, int column) {
        if (!INTEGER.matcher(value).matches()) {
            add(errors, source, column, INVALID_INTEGER_FORMAT);
            return null;
        }
        // Compare digit strings first: no huge BigInteger allocation for an oversized input field.
        String bound = maximum.toString();
        if (value.length() > bound.length() || (value.length() == bound.length() && value.compareTo(bound) > 0)) {
            add(errors, source, column, VALUE_OUT_OF_RANGE);
            return null;
        }
        return new BigInteger(value);
    }

    private BigDecimal money(String value, List<BusinessValidationIssue> errors, ParsedCsvRecord source, int column) {
        if (!MONEY.matcher(value).matches()) {
            add(errors, source, column, INVALID_MONEY_FORMAT);
            return null;
        }
        return new BigDecimal(value);
    }

    private void add(List<BusinessValidationIssue> errors, ParsedCsvRecord source, int column,
                     BusinessValidationIssue.Code code) {
        errors.add(new BusinessValidationIssue(code, COLUMNS.get(column), source.recordNumber(),
                source.startLine(), source.endLine()));
    }
}
