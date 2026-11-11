package vn.edu.wspm.backend.services.mining.conversion;

import java.math.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.edu.wspm.backend.services.mining.PreparedSnapshotMiner;
import vn.edu.wspm.backend.services.mining.conversion.ConvertedOrderSnapshot.*;

/** Pure bounded WDM CSV converter. No Spring bean, IO, catalog access or snapshot state mutation. */
public final class OrderSnapshotConverter {
    public static final String POLICY = "ORDERED_AT_PRODUCT_PRESENCE_V1";
    public static final String HEADER = "order_id,order_item_id,customer_code,ordered_at,status,product_id,"
            + "sku_snapshot,product_name_snapshot,quantity,list_unit_price,allocated_discount_amount,line_total,currency_code";
    private static final Logger log = LoggerFactory.getLogger(OrderSnapshotConverter.class);
    private static final BigInteger MAX_LINE_ID = new BigInteger("18446744073709551615");
    private static final BigInteger MAX_QUANTITY = new BigInteger("4294967295");
    // Validated UTF-8 scalar strings only. No locale/case/normalization or UTF-16 order.
    private static final Comparator<String> EXACT = (a,b) -> Arrays.compareUnsigned(
            a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    private static final Comparator<Row> HISTORY = Comparator.comparing(Row::time)
            .thenComparing(Row::orderId, EXACT).thenComparing(Row::lineId);

    /** Server-owned resource limits, explicit per invocation; no runtime/demo defaults. */
    public record Limits(int maxRawBytes, int maxRows, int maxFieldCodePoints, int maxOutputBytes) {
        public Limits {
            if (maxRawBytes < 1 || maxRows < 0 || maxFieldCodePoints < 1 || maxOutputBytes < 1)
                throw new IllegalArgumentException("Invalid converter limits");
        }
    }
    public static final class ConversionException extends IllegalArgumentException {
        private final String code;
        private final int record;
        private final String field;
        private ConversionException(String code, int record, String field) {
            super(code); this.code = code; this.record = record; this.field = field;
        }
        public String code() { return code; }
        /** One-based logical CSV record, including header; 0 for artifact/output errors. */
        public int record() { return record; }
        public String field() { return field; }
    }
    private record Row(String orderId, BigInteger lineId, String customer, Instant time, String timestamp,
                       String product, String sku, String name, String currency) {}
    private record Label(String name, String sku) {}

    public ConvertedOrderSnapshot convert(byte[] raw, Limits limits) {
        Objects.requireNonNull(raw); Objects.requireNonNull(limits);
        if (raw.length > limits.maxRawBytes()) throw error("RAW_LIMIT_EXCEEDED", 0, "artifact");
        // Snapshot caller bytes once: all parsing and raw hashing refer to the same local copy.
        byte[] bytes = raw.clone();
        String csv;
        try {
            csv = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException invalid) { throw error("INVALID_UTF8", 0, "artifact"); }
        if (csv.startsWith("\uFEFF")) csv = csv.substring(1);
        var records = parse(csv, limits);
        if (records.isEmpty() || !records.getFirst().equals(List.of(HEADER.split(","))))
            throw error("INVALID_HEADER", 1, "header");
        var rows = new ArrayList<Row>();
        var lines = new HashSet<BigInteger>();
        var orders = new HashMap<String, Row>();
        for (int i = 1; i < records.size(); i++) {
            var row = validate(records.get(i), i + 1);
            if (!lines.add(row.lineId())) throw error("DUPLICATE_LINE_ID", i + 1, "order_item_id");
            var previous = orders.putIfAbsent(row.orderId(), row);
            if (previous != null && (!previous.customer().equals(row.customer())
                    || !previous.time().equals(row.time()) || !previous.currency().equals(row.currency())))
                throw error("ORDER_CONFLICT", i + 1, "order_id");
            rows.add(row);
        }
        var result = build(bytes, rows, limits);
        log.debug("Order snapshot converted: rows={}, sequences={}, items={}", rows.size(),
                result.statistics().sequenceCount(), result.items().size());
        return result;
    }

    /** CSV state machine preserves quoted CR/LF and escaped quotes; rejects malformed record tails. */
    private static List<List<String>> parse(String csv, Limits limits) {
        var records = new ArrayList<List<String>>();
        var fields = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false, closed = false, started = false;
        int fieldPoints = 0;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') { field.append('"'); fieldPoints++; i++; }
                    else { quoted = false; closed = true; }
                } else { field.append(c); if (!Character.isLowSurrogate(c)) fieldPoints++; }
            } else if (c == ',' || c == '\r' || c == '\n') {
                fields.add(field.toString()); field.setLength(0); fieldPoints = 0;
                started = false; closed = false;
                if (fields.size() > 13) throw error("INVALID_RECORD_WIDTH", records.size() + 1, "record");
                if (c != ',') {
                    if (c == '\r') {
                        if (i + 1 >= csv.length() || csv.charAt(i + 1) != '\n')
                            throw error("INVALID_CSV", records.size() + 1, "record");
                        i++;
                    }
                    addRecord(records, fields, limits); fields = new ArrayList<>();
                }
            } else if (closed) { throw error("INVALID_CSV", records.size() + 1, "record");
            } else if (c == '"') {
                if (started) throw error("INVALID_CSV", records.size() + 1, "record");
                quoted = true; started = true;
            } else {
                field.append(c); started = true; if (!Character.isLowSurrogate(c)) fieldPoints++;
            }
            if (fieldPoints > limits.maxFieldCodePoints())
                throw error("FIELD_LIMIT_EXCEEDED", records.size() + 1, "record");
        }
        if (quoted) throw error("INVALID_CSV", records.size() + 1, "record");
        if (started || closed || !fields.isEmpty()) {
            fields.add(field.toString()); addRecord(records, fields, limits);
        }
        return records;
    }
    private static void addRecord(List<List<String>> records, List<String> fields, Limits limits) {
        if (fields.size() != 13) throw error("INVALID_RECORD_WIDTH", records.size() + 1, "record");
        if ((long) records.size() >= (long) limits.maxRows() + 1)
            throw error("ROW_LIMIT_EXCEEDED", records.size() + 1, "record");
        records.add(List.copyOf(fields));
    }
    private static Row validate(List<String> f, int record) {
        var names = HEADER.split(",");
        int[] lengths = {40,20,80,27,9,40,80,200,10,20,20,20,3};
        for (int i = 0; i < f.size(); i++) {
            String value = f.get(i);
            if (value.isBlank() || value.indexOf('\0') >= 0
                    || value.codePointCount(0, value.length()) > lengths[i])
                throw error("INVALID_FIELD", record, names[i]);
        }
        BigInteger line = integer(f.get(1), MAX_LINE_ID, record, "order_item_id");
        BigInteger quantity = integer(f.get(8), MAX_QUANTITY, record, "quantity");
        if (!f.get(4).equals("COMPLETED")) throw error("INVALID_STATUS", record, "status");
        if (!f.get(12).matches("[A-Z]{3}")) throw error("INVALID_CURRENCY", record, "currency_code");
        String timestamp = f.get(3);
        if (!timestamp.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{6}Z"))
            throw error("INVALID_TIMESTAMP", record, "ordered_at");
        final Instant time;
        try {
            // LocalDateTime strict parsing rejects leap seconds, 24:00 and invalid calendar dates.
            var local = LocalDateTime.parse(timestamp.substring(0, timestamp.length() - 1));
            if (local.getYear() < 1000) throw new DateTimeException("outside DATETIME domain");
            time = local.toInstant(ZoneOffset.UTC);
        } catch (DateTimeException invalid) { throw error("INVALID_TIMESTAMP", record, "ordered_at"); }
        var price = money(f.get(9), record, names[9]);
        var discount = money(f.get(10), record, names[10]);
        var total = money(f.get(11), record, names[11]);
        var gross = price.multiply(new BigDecimal(quantity));
        if (discount.compareTo(gross) > 0 || total.compareTo(gross.subtract(discount)) != 0)
            throw error("INVALID_LINE_AMOUNT", record, "line_total");
        return new Row(f.get(0), line, f.get(2), time, timestamp, f.get(5), f.get(6), f.get(7), f.get(12));
    }
    private static BigInteger integer(String s, BigInteger max, int record, String field) {
        if (!s.matches("[1-9][0-9]*")) throw error("INVALID_INTEGER", record, field);
        var value = new BigInteger(s);
        if (value.compareTo(max) > 0) throw error("INVALID_INTEGER", record, field);
        return value;
    }
    private static BigDecimal money(String s, int record, String field) {
        if (!s.matches("[0-9]{1,17}\\.[0-9]{2}")) throw error("INVALID_MONEY", record, field);
        return new BigDecimal(s);
    }

    private static ConvertedOrderSnapshot build(byte[] raw, List<Row> rows, Limits limits) {
        var histories = new TreeMap<String, List<Row>>(EXACT);
        var customers = new TreeMap<String, Map<String, List<Row>>>(EXACT);
        for (var row : rows) {
            histories.computeIfAbsent(row.product(), unused -> new ArrayList<>()).add(row);
            customers.computeIfAbsent(row.customer(), unused -> new HashMap<>())
                    .computeIfAbsent(row.orderId(), unused -> new ArrayList<>()).add(row);
        }
        var items = new ArrayList<Item>();
        var itemIds = new HashMap<String, Integer>();
        for (var entry : histories.entrySet()) {
            int id = items.size() + 1;
            Row latest = entry.getValue().stream().max(HISTORY).orElseThrow();
            int variants = (int) entry.getValue().stream().map(r -> new Label(r.name(), r.sku())).distinct().count();
            items.add(new Item(id, entry.getKey(), latest.name(), latest.sku(), latest.lineId().toString(), variants));
            itemIds.put(entry.getKey(), id);
        }
        var spmf = new BoundedText(limits.maxOutputBytes());
        var mapping = new BoundedText(limits.maxOutputBytes());
        mapping.add("{\"version\":" + json(POLICY) + ",\"items\":[");
        for (var item : items) {
            if (item.itemId() > 1) mapping.add(",");
            mapping.add("{\"itemId\":" + item.itemId() + ",\"productId\":" + json(item.productId())
                    + ",\"nameSnapshot\":" + json(item.nameSnapshot()) + ",\"skuSnapshot\":" + json(item.skuSnapshot())
                    + ",\"labelSourceLineId\":" + json(item.labelSourceLineId())
                    + ",\"labelVariantCount\":" + item.labelVariantCount() + "}");
        }
        mapping.add("],\"sequences\":[");
        var sequences = new ArrayList<CustomerSequence>();
        var occurrence = new long[items.size()]; var support = new int[items.size()];
        long itemsets = 0, total = 0, min = Long.MAX_VALUE, max = 0;
        int single = 0;
        for (var customer : customers.entrySet()) {
            int sid = sequences.size() + 1;
            var ordered = new ArrayList<>(customer.getValue().values());
            ordered.sort(Comparator.<List<Row>, Instant>comparing(r -> r.getFirst().time())
                    .thenComparing(r -> r.getFirst().orderId(), EXACT));
            if (sid > 1) mapping.add(",");
            mapping.add("{\"sid\":" + sid + ",\"customerCode\":" + json(customer.getKey()) + ",\"orders\":[");
            var orderReferences = new ArrayList<Order>();
            var seen = new HashSet<Integer>();
            long length = 0;
            for (var order : ordered) {
                Row first = order.getFirst();
                if (!orderReferences.isEmpty()) mapping.add(",");
                mapping.add("{\"orderId\":" + json(first.orderId()) + ",\"orderedAt\":" + json(first.timestamp()) + "}");
                orderReferences.add(new Order(first.orderId(), first.timestamp()));
                var presence = new TreeSet<Integer>();
                for (var row : order) presence.add(itemIds.get(row.product()));
                for (int id : presence) { spmf.add(id + " "); occurrence[id - 1]++; seen.add(id); }
                spmf.add("-1 "); length += presence.size(); itemsets++;
            }
            spmf.add("-2\n"); mapping.add("]}");
            for (int id : seen) support[id - 1]++;
            total += length; min = Math.min(min, length); max = Math.max(max, length);
            if (ordered.size() == 1) single++;
            sequences.add(new CustomerSequence(sid, customer.getKey(), orderReferences));
        }
        mapping.add("]}\n");
        var perItem = new ArrayList<ItemStatistic>();
        for (int i = 0; i < items.size(); i++) perItem.add(new ItemStatistic(i + 1, occurrence[i], support[i]));
        var stats = new Statistics(rows.size(), itemsets, sequences.size(), items.size(), total,
                rows.size() - total, single, sequences.size() - single,
                sequences.isEmpty() ? null : min, sequences.isEmpty() ? null : max, perItem);
        byte[] spmfBytes = spmf.bytes(), mappingBytes = mapping.bytes();
        return new ConvertedOrderSnapshot(POLICY, sequences.isEmpty() ? Outcome.EMPTY : Outcome.CONVERTED,
                PreparedSnapshotMiner.sha256(raw), spmfBytes, PreparedSnapshotMiner.sha256(spmfBytes),
                mappingBytes, PreparedSnapshotMiner.sha256(mappingBytes), items, sequences, stats);
    }

    private static String json(String value) {
        var b = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 32) b.append(String.format(Locale.ROOT, "\\u%04x", (int)c));
            else b.append(c);
        }
        return b.append('"').toString();
    }
    private static final class BoundedText {
        private final int limit;
        private final java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        BoundedText(int limit) { this.limit = limit; }
        void add(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            if ((long) output.size() + bytes.length > limit) throw error("OUTPUT_LIMIT_EXCEEDED", 0, "artifact");
            output.writeBytes(bytes);
        }
        byte[] bytes() { return output.toByteArray(); }
    }
    private static ConversionException error(String code, int record, String field) {
        return new ConversionException(code, record, field);
    }
}
