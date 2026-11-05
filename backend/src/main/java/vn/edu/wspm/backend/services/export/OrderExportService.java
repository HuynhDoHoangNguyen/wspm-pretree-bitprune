package vn.edu.wspm.backend.services.export;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.export.OrderExportRepository;
import vn.edu.wspm.backend.repositories.export.OrderExportRow;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
public class OrderExportService {
    private final OrderExportRepository repository;
    private final OrderCsvWriter writer;
    private final int maxRows;
    private final int maxBytes;

    public OrderExportService(
            OrderExportRepository repository,
            OrderCsvWriter writer,
            @Value("${wdm.order-export.max-rows:100000}") int maxRows,
            @Value("${wdm.order-export.max-bytes:104857600}") int maxBytes) {
        if (maxRows < 1 || maxRows == Integer.MAX_VALUE || maxBytes < 1) {
            throw new IllegalArgumentException(
                    "Order export limits must be positive and leave room for a sentinel row");
        }
        this.repository = repository;
        this.writer = writer;
        this.maxRows = maxRows;
        this.maxBytes = maxBytes;
    }

    public byte[] export(OffsetDateTime fromTime, OffsetDateTime toTime) {
        try {
            List<OrderExportRow> rows;
            try {
                rows = repository.findCompletedInPeriod(fromTime, toTime, maxRows);
            } catch (IllegalArgumentException invalid) {
                log.warn("Order CSV export rejected: invalid period");
                throw ApplicationException.invalid(
                        "period",
                        "INVALID_PERIOD",
                        "Period must increase and fit UTC MySQL dates with microsecond precision or"
                            + " less.");
            }
            byte[] csv = writer.serialize(rows, maxBytes);
            log.info(
                    "Order CSV export completed: rowCount={}, sizeBytes={}",
                    rows.size(),
                    csv.length);
            return csv;
        } catch (OrderExportRepository.ResultLimitExceededException
                | OrderCsvWriter.SizeLimitExceededException limit) {
            log.warn("Order CSV export rejected: configured result limit exceeded");
            throw new ApplicationException(
                    ErrorCode.PAYLOAD_TOO_LARGE,
                    "Export exceeds configured limits; select a smaller period.");
        } catch (ApplicationException expected) {
            throw expected;
        } catch (RuntimeException unexpected) {
            var safe =
                    new IllegalStateException(
                            "Order CSV export failed: " + unexpected.getClass().getSimpleName());
            safe.setStackTrace(unexpected.getStackTrace());
            log.error("Order CSV export failed", safe);
            throw new ApplicationException(
                    ErrorCode.INTERNAL_ERROR, "Order export could not be completed.");
        }
    }
}
