package vn.edu.wspm.backend.controllers.api.v1.admin;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.services.export.OrderExportService;
import vn.edu.wspm.backend.web.common.RequestChecks;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/order-exports")
public class OrderExportController {
    private static final Set<String> FILTERS = Set.of("fromTime", "toTime");
    private static final Pattern EXCESS_PRECISION = Pattern.compile("\\.[0-9]{7,}");
    private static final MediaType CSV = MediaType.parseMediaType("text/csv; charset=UTF-8");
    private final OrderExportService service;

    @GetMapping(produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam MultiValueMap<String, String> parameters) {
        RequestChecks.requireAllowedQueryNames(parameters.keySet(), FILTERS,
                "UNKNOWN_FIELD", "Query contains an unsupported filter.");
        OffsetDateTime fromTime = timestamp(parameters, "fromTime");
        OffsetDateTime toTime = timestamp(parameters, "toTime");
        byte[] csv = service.export(fromTime, toTime);
        return ResponseEntity.ok()
                .contentType(CSV)
                .contentLength(csv.length)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("order-export-" + UUID.randomUUID() + ".csv")
                                .build()
                                .toString())
                .body(csv);
    }

    private static OffsetDateTime timestamp(
            MultiValueMap<String, String> parameters, String field) {
        var values = parameters.get(field);
        if (values == null
                || values.size() != 1
                || values.getFirst() == null
                || EXCESS_PRECISION.matcher(values.getFirst()).find()) {
            throw ApplicationException.invalid(
                    field,
                    "INVALID_TIMESTAMP",
                    "Supply one timestamp with explicit offset and at most six fractional digits.");
        }
        try {
            return OffsetDateTime.parse(values.getFirst());
        } catch (DateTimeParseException invalid) {
            throw ApplicationException.invalid(
                    field, "INVALID_TIMESTAMP", "Timestamp is not a valid date and time.");
        }
    }
}
