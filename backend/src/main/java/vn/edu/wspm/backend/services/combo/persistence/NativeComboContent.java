package vn.edu.wspm.backend.services.combo.persistence;

import java.util.*;
import vn.edu.wspm.backend.services.combo.grouping.CandidateGrouping;

/** Native publication + signed context reproduce the complete input; DB representation is stored separately. */
public final class NativeComboContent {
    private NativeComboContent() {}
    public static byte[] encode(CandidateGrouping.Result grouping, DraftBatchWrite.Plan plan, int storedLimit) {
        var ordered=new CandidateGrouping.Result(true,List.of(),grouping.groups().stream()
                .sorted(Comparator.comparing(g->g.structureKey().serialized())).toList());
        return envelope(Map.of("version","COMBO_DRAFT_CONTENT_V1","grouping",ordered,"plan",plan),storedLimit);
    }
    public static byte[] envelope(Object fullContent,int storedLimit) {
        try { return BatchCanonical.encode(fullContent,storedLimit); }
        catch(IllegalArgumentException e) {
            if(!"PAYLOAD_LIMIT_EXCEEDED".equals(e.getMessage()))throw e;
        }
        // Preserve V1 bytes for existing identities. Larger native payloads retain
        // an exact full-content digest; never truncate groups or use top-K as a batch.
        byte[] full=BatchCanonical.encode(fullContent,8*1024*1024);
        return BatchCanonical.encode(Map.of("version","NATIVE_COMBO_CONTENT_DIGEST_V2",
                "canonicalVersion","COMBO_DRAFT_CONTENT_V1","payloadSha256",JdbcDraftBatchStore.hash(full),
                "payloadSize",full.length),storedLimit);
    }
}
