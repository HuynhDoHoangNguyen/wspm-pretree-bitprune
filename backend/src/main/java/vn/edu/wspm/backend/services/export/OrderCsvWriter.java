package vn.edu.wspm.backend.services.export;

import org.springframework.stereotype.Component;

import vn.edu.wspm.backend.repositories.export.OrderExportRow;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Locale;

/** UTF-8 CSV with a leading BOM for Excel; never trims text or recalculates line amounts. */
@Component
public class OrderCsvWriter {
    private static final String HEADER =
            "order_id,order_item_id,customer_code,ordered_at,status,product_id,sku_snapshot,product_name_snapshot,quantity,list_unit_price,allocated_discount_amount,line_total,currency_code\r\n";
    private static final DateTimeFormatter TIMESTAMP =
            new DateTimeFormatterBuilder().appendInstant(6).toFormatter(Locale.ROOT);

    public byte[] serialize(List<OrderExportRow> rows, int maxBytes) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // Excel's direct-open path uses this signature to recognize UTF-8.
        // Count it through the same byte bound as the header and records.
        append(output, "\uFEFF", maxBytes);
        append(output, HEADER, maxBytes);
        for (OrderExportRow row : rows) {
            if (row.orderedAt().getNano() % 1_000 != 0) {
                throw new IllegalStateException(
                        "Historical timestamp exceeds microsecond precision");
            }
            List<String> fields =
                    List.of(
                            row.orderId(),
                            row.orderItemId().toString(),
                            row.customerCode(),
                            TIMESTAMP.format(row.orderedAt()),
                            row.status(),
                            row.productId(),
                            row.skuSnapshot(),
                            row.productNameSnapshot(),
                            Long.toString(row.quantity()),
                            money(row.listUnitPrice()),
                            money(row.allocatedDiscountAmount()),
                            money(row.lineTotal()),
                            row.currencyCode());
            String record =
                    fields.stream()
                                    .map(OrderCsvWriter::quote)
                                    .collect(java.util.stream.Collectors.joining(","))
                            + "\r\n";
            append(output, record, maxBytes);
        }
        return output.toByteArray();
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String quote(String value) {
        if (value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static void append(ByteArrayOutputStream output, String value, int maxBytes) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if ((long) output.size() + bytes.length > maxBytes) {
            throw new SizeLimitExceededException();
        }
        output.writeBytes(bytes);
    }

    public static class SizeLimitExceededException extends RuntimeException {}
}
