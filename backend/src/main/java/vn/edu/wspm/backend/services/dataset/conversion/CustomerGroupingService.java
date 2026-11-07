package vn.edu.wspm.backend.services.dataset.conversion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.services.dataset.csv.ParsedCsvRecord;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;

/** Pure in-memory grouping of the accepted kept-record handoff; no catalog lookup or cleaning replay. */
@Service
@Slf4j
public class CustomerGroupingService {
    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    public GroupedDataset group(VerifiedConversionInput input, ProductItemDictionary dictionary) {
        Objects.requireNonNull(input, "verified input");
        Objects.requireNonNull(dictionary, "product dictionary");
        var products = new HashSet<String>();
        var recordNumbers = new HashSet<Long>();
        var lineIds = new HashSet<String>();
        var orders = new LinkedHashMap<String, OrderBuilder>();
        for (var record : input.keptRecords()) {
            var fields = record.fields();
            if (fields.size() != 13 || record.recordNumber() < 2 || !recordNumbers.add(record.recordNumber())) {
                throw invalid("RECORD_IDENTITY");
            }
            String orderId = identity(fields.get(0), 40);
            String customer = identity(fields.get(2), 80);
            String product = identity(fields.get(5), 40);
            if (!lineIds.add(identity(fields.get(1), 20))) throw invalid("DUPLICATE_KEPT_LINE");
            Instant orderedAt = timestamp(fields.get(3));
            String currency = fields.get(12);
            Integer item = dictionary.productToItem().get(product);
            if (item == null) throw invalid("MISSING_MAPPING");
            products.add(product);
            var order = orders.computeIfAbsent(orderId, key -> new OrderBuilder(customer, orderedAt, currency));
            if (!order.customer.equals(customer) || !order.orderedAt.equals(orderedAt) || !order.currency.equals(currency)) {
                throw invalid("ORDER_METADATA_CONFLICT");
            }
            order.items.add(item); // Itemset membership, never multiply by quantity or sum line amounts.
            order.records.add(record);
        }
        if (!products.equals(dictionary.productToItem().keySet())) throw invalid("DICTIONARY_PRODUCT_SET");

        var customers = new LinkedHashMap<String, LinkedHashMap<String, GroupedDataset.GroupedOrder>>();
        orders.forEach((id, order) -> customers.computeIfAbsent(order.customer, key -> new LinkedHashMap<>())
                .put(id, new GroupedDataset.GroupedOrder(id, order.orderedAt, order.currency,
                        List.copyOf(order.items), order.records)));
        var groups = new LinkedHashMap<String, GroupedDataset.CustomerGroup>();
        customers.forEach((code, groupedOrders) -> groups.put(code, new GroupedDataset.CustomerGroup(code, groupedOrders)));
        log.info("Customer grouping completed: customers={}, orders={}, records={}",
                groups.size(), orders.size(), input.keptRecords().size());
        return new GroupedDataset(groups);
    }

    private static String identity(String value, int maximum) {
        if (value.isBlank() || value.codePointCount(0, value.length()) > maximum) throw invalid("IDENTITY_SHAPE");
        return value;
    }

    private static Instant timestamp(String value) {
        // Decode the existing CSV-v1 UTC/microsecond representation; never round or infer an order.
        if (value.length() != 27) throw invalid("TIMESTAMP_SHAPE");
        try { return LocalDateTime.parse(value, UTC_TIME).toInstant(ZoneOffset.UTC); }
        catch (DateTimeException malformed) { throw invalid("TIMESTAMP_SHAPE"); }
    }

    private static ApplicationException invalid(String reason) {
        log.warn("Customer grouping rejected: reason={}", reason);
        return new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE, "Verified grouping input is inconsistent.");
    }

    private static final class OrderBuilder {
        private final String customer;
        private final Instant orderedAt;
        private final String currency;
        private final TreeSet<Integer> items = new TreeSet<>();
        private final List<ParsedCsvRecord> records = new ArrayList<>();
        private OrderBuilder(String customer, Instant orderedAt, String currency) {
            this.customer = customer;
            this.orderedAt = orderedAt;
            this.currency = currency;
        }
    }
}
