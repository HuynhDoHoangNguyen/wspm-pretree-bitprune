package vn.edu.wspm.backend.services.dataset.conversion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;

/** In-memory canonical UTF-8 SPMF bytes. Input chronology is supplied by WDM-03.3, never inferred here. */
@Service
@Slf4j
public class SpmfDatasetSerializer {
    public static final String POLICY_VERSION = "ORDERED_CUSTOMER_SPMF_UTF8_LF_V1";
    private static final byte[] ITEMSET_END = "-1 ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SEQUENCE_END = "-2\n".getBytes(StandardCharsets.US_ASCII);

    public byte[] serialize(OrderedDataset input) {
        Objects.requireNonNull(input, "ordered input");
        var output = new ByteArrayOutputStream();
        var customers = new HashSet<String>();
        var orders = new HashSet<String>();
        for (var customer : input.customers()) {
            if (customer.customerCode().isBlank() || !customers.add(customer.customerCode()) || customer.orders().isEmpty()) {
                throw invalid("SEQUENCE_STRUCTURE");
            }
            for (var order : customer.orders()) {
                if (order.orderId().isBlank() || !orders.add(order.orderId()) || order.itemIds().isEmpty()) {
                    throw invalid("ITEMSET_STRUCTURE");
                }
                int previous = 0;
                for (int item : order.itemIds()) {
                    if (item <= previous) throw invalid("ITEM_ID_ORDER_OR_RANGE");
                    previous = item;
                    output.writeBytes(Integer.toString(item).getBytes(StandardCharsets.US_ASCII));
                    output.write(' ');
                }
                output.writeBytes(ITEMSET_END);
            }
            output.writeBytes(SEQUENCE_END);
        }
        var bytes = output.toByteArray();
        log.info("SPMF serialization completed: sequences={}, itemsets={}, bytes={}", customers.size(), orders.size(), bytes.length);
        return bytes;
    }

    private static ApplicationException invalid(String reason) {
        log.warn("SPMF serialization rejected: reason={}", reason);
        return new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE, "Ordered SPMF input is inconsistent.");
    }
}
