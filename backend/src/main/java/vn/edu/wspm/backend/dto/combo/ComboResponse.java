package vn.edu.wspm.backend.dto.combo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ComboResponse(
        String comboId,
        String runId,
        String comboName,
        String comboType,
        Double comboScore,
        BigDecimal comboPrice,
        Long support,
        Double weight,
        String comboStatus,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        List<ComboItemResponse> items
) {
}

