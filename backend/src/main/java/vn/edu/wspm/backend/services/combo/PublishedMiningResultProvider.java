package vn.edu.wspm.backend.services.combo;

import vn.edu.wspm.backend.dto.run.result.RunResultResponse.PublishedBundle;

/** Input boundary for future combo generation. No ranking, flattening, pricing or combo writes. */
public interface PublishedMiningResultProvider {
    PublishedBundle requireEligible(String runId);
}
