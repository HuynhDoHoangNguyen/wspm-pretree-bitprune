package vn.edu.wspm.backend.services.dataset.validation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record ValidationLimits(int maxRecordCharacters, int maxRows, int maxReportBytes,
                               long maxRawBytes, long maxDurationMillis, int maxConcurrent) {
    public ValidationLimits(
            @Value("${wdm.validation.max-record-characters:16384}") int maxRecordCharacters,
            @Value("${wdm.validation.max-rows:100000}") int maxRows,
            @Value("${wdm.validation.max-report-bytes:8388608}") int maxReportBytes,
            @Value("${wdm.validation.max-raw-bytes:16777216}") long maxRawBytes,
            @Value("${wdm.validation.max-duration-millis:30000}") long maxDurationMillis,
            @Value("${wdm.validation.max-concurrent:2}") int maxConcurrent) {
        if (maxRecordCharacters <= 0 || maxRows <= 0 || maxReportBytes <= 0 || maxRawBytes <= 0
                || maxDurationMillis <= 0 || maxDurationMillis > 300_000 || maxConcurrent <= 0) {
            throw new IllegalArgumentException("Validation limits must be positive; duration must not exceed five minutes");
        }
        this.maxRecordCharacters = maxRecordCharacters;
        this.maxRows = maxRows;
        this.maxReportBytes = maxReportBytes;
        this.maxRawBytes = maxRawBytes;
        this.maxDurationMillis = maxDurationMillis;
        this.maxConcurrent = maxConcurrent;
    }
}
