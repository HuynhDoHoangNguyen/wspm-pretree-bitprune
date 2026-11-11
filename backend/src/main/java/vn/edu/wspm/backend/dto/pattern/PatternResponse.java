package vn.edu.wspm.backend.dto.pattern;

import java.util.List;

public record PatternResponse(
        Long patternId,
        String runId,
        Long support,
        Double weight,
        Integer patternLength,
        List<PatternItemResponse> items
) {
}

