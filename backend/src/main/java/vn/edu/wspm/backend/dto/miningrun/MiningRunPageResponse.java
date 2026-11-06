package vn.edu.wspm.backend.dto.miningrun;

import java.util.List;

public record MiningRunPageResponse(
        List<MiningRunSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
