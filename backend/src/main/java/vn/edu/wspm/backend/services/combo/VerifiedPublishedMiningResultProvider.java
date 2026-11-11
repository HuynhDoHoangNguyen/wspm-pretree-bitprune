package vn.edu.wspm.backend.services.combo;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import vn.edu.wspm.backend.dto.run.result.RunResultResponse.PublishedBundle;
import vn.edu.wspm.backend.repositories.run.RunResultPublicationRepository;
import vn.edu.wspm.backend.services.run.query.RunResultQueryService;

@Service
@Slf4j
public class VerifiedPublishedMiningResultProvider implements PublishedMiningResultProvider {
    private final RunResultQueryService reads;
    public VerifiedPublishedMiningResultProvider(RunResultQueryService reads) { this.reads=reads; }
    @Override public PublishedBundle requireEligible(String runId) {
        var bundle=reads.publishedBundle(runId);
        if (!bundle.result().eligibleForCombo() || bundle.patterns().isEmpty()) {
            log.warn("Published combo input rejected: runId={}",runId);
            throw RunResultPublicationRepository.conflict();
        }
        log.debug("Published combo input read: runId={}, patterns={}",runId,bundle.patterns().size());
        return bundle;
    }
}
