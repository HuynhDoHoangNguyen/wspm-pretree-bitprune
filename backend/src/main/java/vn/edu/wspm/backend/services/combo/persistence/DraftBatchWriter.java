package vn.edu.wspm.backend.services.combo.persistence;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static vn.edu.wspm.backend.services.combo.persistence.DraftBatchWrite.*;

/** Fail-closed entry point. No database adapter until storage contracts are approved. */
public final class DraftBatchWriter {
    private static final Logger log=LoggerFactory.getLogger(DraftBatchWriter.class);
    private static final List<String> BLOCKERS=List.of("POLICY_EVIDENCE_STORAGE_UNCONFIRMED",
            "IDEMPOTENCY_STORAGE_UNCONFIRMED","COMMERCIAL_WRITER_CONTRACT_UNCONFIRMED");
    public WriteResult write(Request request) {
        var validation=new DraftBatchPlanner().plan(request);
        if(!validation.valid()) return new WriteResult("INVALID",validation.errors(),null);
        log.warn("Draft batch write blocked: storage contracts require approval");
        return new WriteResult("BLOCKED",BLOCKERS,validation.plan());
    }
}
