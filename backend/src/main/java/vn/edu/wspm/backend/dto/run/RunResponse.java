package vn.edu.wspm.backend.dto.run;

import java.time.LocalDateTime;

public record RunResponse(
        String runId,
        String algorithm,
        Double minSup,
        Double minWeight,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String status,
        Long patternCount
) {
}

