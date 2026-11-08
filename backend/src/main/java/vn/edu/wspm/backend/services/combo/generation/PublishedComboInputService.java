package vn.edu.wspm.backend.services.combo.generation;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.dto.pattern.PatternDetailResponse;
import vn.edu.wspm.backend.dto.run.result.RunResultResponse;
import vn.edu.wspm.backend.services.combo.PublishedMiningResultProvider;
import vn.edu.wspm.backend.services.combo.evaluation.CandidateEvaluation;
import vn.edu.wspm.backend.services.combo.evaluation.PatternCandidateEvaluator;

/** Consumes the producer's verified complete publication; never assembles pages or issues a receipt. */
@Service
public final class PublishedComboInputService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PublishedComboInputService.class);
    private final PublishedMiningResultProvider producer;
    public PublishedComboInputService(PublishedMiningResultProvider producer) {
        this.producer = Objects.requireNonNull(producer);
    }
    public record Summary(String runId, String snapshotId, String publicationDigest,
            String patternCount, String dictionaryCount, String inputStatus,
            String generationStatus, List<String> blockers) {
        public Summary { blockers = List.copyOf(blockers); }
    }
    /** Internal server-owned context. It must not be bound from an HTTP approval payload. */
    public record BusinessContext(CandidateEvaluation.Purpose purpose, String purposeReference,
            CandidateEvaluation.Policy policy, Map<String, CandidateEvaluation.CatalogProduct> catalog,
            String catalogReference, Instant evaluatedAt) {
        public BusinessContext { catalog = Map.copyOf(catalog); }
    }
    public static final class Prepared {
        private final RunResultResponse.Metadata metadata;
        private final List<PatternDetailResponse> patterns;
        private final List<CandidateEvaluation.DictionaryItem> dictionary;
        Prepared(RunResultResponse.PublishedBundle bundle) {
            metadata = bundle.result();
            dictionary = bundle.runItems().stream().map(d -> new CandidateEvaluation.DictionaryItem(
                    metadata.runId(), d.itemId(), d.productId(), d.productNameSnapshot(), d.skuSnapshot())).toList();
            patterns = bundle.patterns().stream().map(p -> {
                var h = p.pattern();
                return new PatternDetailResponse(h.runId(), h.patternId(), h.canonicalPattern(), h.supportAbs(),
                        h.supportRatio(), h.weightedSupport(), h.patternWeight(), h.patternWeightPolicy(),
                        h.patternItemCount(), h.itemsetCount(), metadata.provenance().sequenceCount(),
                        PatternDetailResponse.IntegrityStatus.COMPLETE, List.of(),
                        p.itemsets().stream().map(s -> new PatternDetailResponse.Itemset(s.itemsetOrder(),
                            s.items().stream().map(i -> new PatternDetailResponse.Item(i.itemOrder(), i.itemId(),
                                PatternDetailResponse.MappingStatus.MAPPED, i.productId(),
                                i.productNameSnapshot(), i.skuSnapshot(), i.itemWeight())).toList())).toList());
            }).toList();
        }
        public List<PatternDetailResponse> patterns() { return patterns; }
        public List<CandidateEvaluation.DictionaryItem> dictionary() { return dictionary; }
        public RunResultResponse.Metadata metadata() { return metadata; }
        public String publishedReference() {
            return "WDM_RESULT_MANIFEST:" + metadata.runId() + ":" + metadata.resultSha256();
        }
        @Override public String toString() { return "PublishedComboInput[redacted]"; }
    }
    public Prepared prepare(String runId) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw vn.edu.wspm.backend.repositories.run.RunResultPublicationRepository.conflict();
        }
        // Provider verifies provenance, complete map/temporal/dictionary and committed manifest
        // under its producer-owned read transaction. No live Outcome or current catalog labels.
        var prepared = new Prepared(producer.requireEligible(runId));
        log.debug("Published Combo input prepared: runId={}, patterns={}, dictionary={}", runId,
                prepared.patterns.size(), prepared.dictionary.size());
        return prepared;
    }
    public Summary summary(String runId) {
        var p = prepare(runId);
        return new Summary(p.metadata.runId(), p.metadata.provenance().snapshotId(),
                p.metadata.resultSha256(), Integer.toString(p.patterns.size()),
                Integer.toString(p.dictionary.size()), "VERIFIED_PUBLISHED_INPUT",
                "BLOCKED", List.of("BUSINESS_POLICY_UNCONFIRMED",
                        "PUBLISHED_RESULT_WRITER_PROTOCOL_REQUIRED", "REVIEW_POLICY_UNCONFIRMED"));
    }
    /** Reuses the frozen TV2 evaluator. Caller resolves business/purpose/catalog independently. */
    public CandidateEvaluation.SessionResult evaluate(Prepared input, BusinessContext business) {
        Objects.requireNonNull(input); Objects.requireNonNull(business);
        var m = input.metadata;
        var evidence = new CandidateEvaluation.Evidence(m.runId(), m.executionStatus(),
                m.provenance().snapshotId(), "VALID", business.purpose(), business.purposeReference(),
                BigInteger.valueOf(input.patterns.size()), true, input.publishedReference(), true,
                business.catalogReference(), business.evaluatedAt());
        return new PatternCandidateEvaluator().evaluate(new CandidateEvaluation.Input(evidence,
                business.policy(), input.patterns, input.dictionary, business.catalog()));
    }
}
