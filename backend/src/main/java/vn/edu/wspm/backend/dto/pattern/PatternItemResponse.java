package vn.edu.wspm.backend.dto.pattern;

public record PatternItemResponse(
        Long patternId,
        Integer itemsetOrder,
        Integer itemOrder,
        Integer itemId
) {
}

