package vn.edu.wspm.backend.services.mining.provider;

import java.io.IOException;
import java.util.*;
import vn.edu.wspm.backend.services.dataset.RawSnapshotStorage;
import vn.edu.wspm.backend.services.mining.provider.SnapshotInputReader.Metadata;
import vn.edu.wspm.backend.services.mining.provider.TrustedRawSnapshotProvider.*;

/** Operator assignment pins an acquisition in THIS application's DB/private storage context.
 * It does not attest truth of CSV contents or convert uploads into DATABASE exports. */
public final class OperatorSourceVerifier implements SourceVerifier {
    public record Assignment(String metadataBinding, String namespace) {}
    public record Check(Optional<SourceProof> proof, String reason) {}
    private final String applicationContext;
    private final Map<String,Assignment> assignments;
    private final RawSnapshotStorage storage;
    public OperatorSourceVerifier(String applicationContext, Map<String,Assignment> assignments, RawSnapshotStorage storage) {
        this.applicationContext=applicationContext; this.assignments=Map.copyOf(assignments); this.storage=Objects.requireNonNull(storage);
    }
    @Override public Optional<SourceProof> verify(Metadata m) { return check(m).proof(); }
    public Check check(Metadata m) {
        if (!validText(applicationContext,80)) return blocked("APPLICATION_CONTEXT_UNCONFIGURED");
        if (!"CSV".equals(m.sourceType()) || !"CSV_UPLOAD".equals(m.acquisition()))
            return blocked("DATABASE_ACQUISITION_ATTESTATION_UNAVAILABLE");
        var assignment=assignments.get(m.id());
        if (assignment==null) return blocked("SNAPSHOT_NAMESPACE_UNASSIGNED");
        if (!validText(assignment.namespace(),160) || !m.binding().equals(assignment.metadataBinding()))
            return blocked("OPERATOR_ASSIGNMENT_MISMATCH");
        if (!"wdm-order-csv-v1".equals(m.contractVersion()) || m.createdAt()==null
                || !Set.of("PENDING","VALID").contains(Objects.toString(m.validationStatus(),""))
                || !"ordered_at".equals(m.timeField()) || !"COMPLETED_CURRENT_AT_ACQUISITION".equals(m.statusPolicy())
                || m.fromTime()!=null || m.toTime()!=null || m.sourceTimeZone()!=null)
            return blocked("ACQUISITION_METADATA_INCONSISTENT");
        // Reuse the real storage ownership/path/bounds/hash protocol, not just availability().
        try { storage.verifiedBytes(m.id(),m.rawReference(),m.rawHash(),m.rawSize()); }
        catch (IOException | IllegalArgumentException failure) { return blocked("ACQUISITION_ARTIFACT_UNVERIFIABLE"); }
        return new Check(Optional.of(new SourceProof(m.binding(),assignment.namespace(),
                applicationContext+":CSV_UPLOAD_OPERATOR_ASSIGNED_V1:"+m.binding())),null);
    }
    private static Check blocked(String reason) { return new Check(Optional.empty(),reason); }
    static boolean validText(String s,int max) { return s!=null && !s.isBlank() && s.codePointCount(0,s.length())<=max
            && s.codePoints().noneMatch(Character::isISOControl)
            && java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(s); }
}
