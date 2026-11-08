package vn.edu.wspm.backend.services.combo.persistence;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static vn.edu.wspm.backend.services.combo.persistence.DraftBatchWrite.*;

/** Trusted SERVER integration, never a boolean or proof object accepted from an HTTP request.
 * Must resolve full mining evidence, reproduce generator/grouping and verify business/commercial
 * approvals, catalog/inventory observation, actor and EVERY meaningful field covered by contentDigest.
 * Calling JdbcDraftBatchStore.miningBinding alone does NOT establish any of those approvals. */
@FunctionalInterface
public interface DraftBatchAuthority {
    Decision authorize(Request request,Plan plan,String contentDigest);
    record Approvals(String generatorVersion,String groupingVersion,String evaluationRef,String weightRef,
                     String rankingRef,String anchorRef,String commercialPolicyRef,String catalogRef) {}
    record Authorization(String contentDigest,String runFactsDigest,String miningReceiptDigest,
                         Approvals approvals,byte[] resolvedEvidence) {
        public Authorization {resolvedEvidence=resolvedEvidence.clone();}
        @Override public byte[] resolvedEvidence(){return resolvedEvidence.clone();}
    }
    record Decision(List<String> blockers,Authorization authorization) {
        public Decision {blockers=List.copyOf(blockers);}
        public static Decision blocked(String code){return new Decision(List.of(code),null);}
    }
    static String miningReference(String runId,String receiptDigest) {
        return "MINING_RUN_COMPLETION:"+Base64.getUrlEncoder().withoutPadding().encodeToString(runId.getBytes(StandardCharsets.UTF_8))+":"+receiptDigest;
    }
    static String nativeReference(String runId,String digest){return "WDM_RESULT_MANIFEST:"+runId+":"+digest;}
}
