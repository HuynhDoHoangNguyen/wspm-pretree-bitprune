package vn.edu.wspm.backend.services.mining.provider;

import java.io.*;
import java.nio.file.NoSuchFileException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.wspm.backend.services.dataset.RawSnapshotStorage;
import vn.edu.wspm.backend.services.mining.PreparedSnapshotMiner;
import vn.edu.wspm.backend.services.mining.conversion.*;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.statistics.ItemStatisticsCalculator;
import vn.edu.wspm.backend.services.mining.provider.SnapshotInputReader.Metadata;

/** Internal in-memory boundary. No bean/HTTP, seal, writes, engine execution or publication. */
public final class TrustedRawSnapshotProvider {
    private static final Logger log = LoggerFactory.getLogger(TrustedRawSnapshotProvider.class);
    public record Configuration(OrderSnapshotConverter.Limits limits, MinimumSupport minimumSupport,
                                String conversionPolicy) {
        public Configuration { Objects.requireNonNull(limits); Objects.requireNonNull(minimumSupport); }
    }
    /** Verify namespace AND acquisition/source/scope ownership against a trusted server registry.
     * A product lookup or self-declared CSV source metadata is not such an attestation. */
    public interface SourceVerifier { Optional<SourceProof> verify(Metadata metadata); }
    public record SourceProof(String metadataBinding, String namespace, String verificationRef) {}
    /** Implementations verify loaded artifact/build identity; never use caller-supplied revision labels. */
    public interface EngineVerifier { Optional<EngineProof> verify(); }
    public record EngineProof(String revision, String artifactSha256, String verificationRef) {}
    public enum State { READY_FOR_MINING, BLOCKED, EMPTY }
    public record Provenance(String snapshotId, String sourceType, String acquisition, String sourceTimeZone,
                             LocalDateTime fromTime, LocalDateTime toTime, String rawSha256, long rawSize,
                             String metadataBinding, Optional<SourceProof> sourceProof,
                             Optional<EngineProof> engineProof) {}
    public static final class Handoff {
        private final State state;
        private final ConvertedOrderSnapshot converted;
        private final Provenance provenance;
        private final List<String> blockers;
        private final PreparedSnapshotMiner.Input minerInput;
        private Handoff(State state, ConvertedOrderSnapshot converted, Provenance provenance,
                        List<String> blockers, PreparedSnapshotMiner.Input input) {
            this.state=state; this.converted=converted; this.provenance=provenance;
            this.blockers=List.copyOf(blockers); this.minerInput=input;
        }
        public State state() { return state; }
        public ConvertedOrderSnapshot converted() { return converted; }
        public Provenance provenance() { return provenance; }
        public List<String> blockers() { return blockers; }
        public Optional<PreparedSnapshotMiner.Input> minerInput() { return Optional.ofNullable(minerInput); }
        @Override public String toString() { return "SnapshotHandoff[state="+state+", sealed=false]"; }
    }
    public static final class ProviderException extends IllegalArgumentException {
        private final String code;
        private final String detailCode;
        private ProviderException(String code, String detailCode) { super(code); this.code=code; this.detailCode=detailCode; }
        public String code() { return code; }
        public String detailCode() { return detailCode; }
    }
    private final SnapshotInputReader reader;
    private final RawSnapshotStorage storage;
    private final Configuration configuration;
    private final SourceVerifier sourceVerifier;
    private final EngineVerifier engineVerifier;
    private final OrderSnapshotConverter converter = new OrderSnapshotConverter();
    public TrustedRawSnapshotProvider(SnapshotInputReader reader, RawSnapshotStorage storage,
                                     Configuration configuration, SourceVerifier sourceVerifier, EngineVerifier engineVerifier) {
        this.reader=Objects.requireNonNull(reader); this.storage=Objects.requireNonNull(storage);
        this.configuration=Objects.requireNonNull(configuration);
        this.sourceVerifier=Objects.requireNonNull(sourceVerifier); this.engineVerifier=Objects.requireNonNull(engineVerifier);
    }
    public Handoff prepare(String snapshotId) {
        try { return prepareInternal(snapshotId); }
        catch (ProviderException failure) { log.warn("Snapshot preparation rejected: code={}",failure.code()); throw failure; }
        catch (DataAccessException failure) {
            // SQL messages may contain payloads/credentials. No raw exception/cause crosses this boundary.
            log.error("Snapshot preparation repository unavailable"); throw error("METADATA_READ_UNAVAILABLE");
        }
        catch (org.springframework.transaction.TransactionException failure) {
            log.error("Snapshot preparation transaction unavailable"); throw error("METADATA_READ_UNAVAILABLE");
        }
    }
    private Handoff prepareInternal(String id) {
        if (id==null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw error("INVALID_SNAPSHOT_ID");
        // Explicitly prohibit holding an outer transaction while doing storage/conversion work.
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw error("ACTIVE_TRANSACTION_UNSUPPORTED");
        if (!OrderSnapshotConverter.POLICY.equals(configuration.conversionPolicy())) throw error("UNSUPPORTED_CONVERSION_POLICY");
        Metadata metadata = reader.read(id,Set.of()).metadata().orElseThrow(()->error("SNAPSHOT_NOT_FOUND"));
        validateMetadata(id,metadata);
        long size;
        try { size=Long.parseLong(metadata.rawSize()); }
        catch (NumberFormatException failure) { throw error("METADATA_INCONSISTENT"); }
        if (size>configuration.limits().maxRawBytes()) throw error("RAW_LIMIT_EXCEEDED");
        byte[] raw;
        try { raw=storage.verifiedBytes(id,metadata.rawReference(),metadata.rawHash(),metadata.rawSize()); }
        catch (NoSuchFileException missing) { throw error("RAW_NOT_FOUND"); }
        catch (IOException | IllegalArgumentException unavailable) { throw error("RAW_UNVERIFIABLE"); }
        if (raw.length!=size || !PreparedSnapshotMiner.sha256(raw).equals(metadata.rawHash())) throw error("RAW_UNVERIFIABLE");
        final ConvertedOrderSnapshot converted;
        try { converted=converter.convert(raw,configuration.limits()); }
        catch (OrderSnapshotConverter.ConversionException failure) { throw new ProviderException("CSV_INVALID",failure.code()); }
        verifyConverted(converted,metadata);
        var finalRead=reader.read(id,new HashSet<>(converted.items().stream().map(ConvertedOrderSnapshot.Item::productId).toList()));
        if (!finalRead.metadata().equals(Optional.of(metadata))) throw error("METADATA_CHANGED");
        for (var item:converted.items()) {
            var matches=finalRead.productMatches().get(item.productId());
            if (matches==null) throw error("PRODUCT_CHECK_UNVERIFIED");
            if (matches.isEmpty()) throw error("PRODUCT_NOT_FOUND");
            if (matches.size()!=1 || !matches.getFirst().equals(item.productId())) throw error("PRODUCT_IDENTITY_COLLISION");
        }
        var proof=Objects.requireNonNull(sourceVerifier.verify(metadata));
        if (proof.isPresent() && (!metadata.binding().equals(proof.get().metadataBinding())
                || !text(proof.get().namespace(),160) || !text(proof.get().verificationRef(),255)))
            throw error("SOURCE_PROOF_INVALID");
        var blockers=new ArrayList<String>();
        if (proof.isEmpty()) blockers.add("SOURCE_NAMESPACE_UNVERIFIED");
        if (converted.outcome()==ConvertedOrderSnapshot.Outcome.EMPTY) {
            if (!reader.read(id,Set.of()).metadata().equals(Optional.of(metadata))) throw error("METADATA_CHANGED");
            return result(State.EMPTY,converted,metadata,size,proof,Optional.empty(),blockers,null);
        }
        var resolved=new MinimumSupportResolver().resolve(configuration.minimumSupport(),converted.statistics().sequenceCount());
        if (!(resolved.effectiveThreshold()>0)) throw error("ZERO_EFFECTIVE_THRESHOLD_UNSUPPORTED");
        var engine=Objects.requireNonNull(engineVerifier.verify());
        if (engine.isPresent() && (!text(engine.get().revision(),160) || !hash(engine.get().artifactSha256())
                || !text(engine.get().verificationRef(),255))) throw error("ENGINE_PROOF_INVALID");
        if (engine.isEmpty()) blockers.add("ENGINE_REVISION_UNVERIFIED");
        // Verifiers can perform slow registry reads; recheck fresh metadata again afterwards.
        if (!reader.read(id,Set.of()).metadata().equals(Optional.of(metadata))) throw error("METADATA_CHANGED");
        PreparedSnapshotMiner.Input input=blockers.isEmpty() ? new PreparedSnapshotMiner.Input(id,metadata.rawHash(),
                converted.spmf(),converted.sequenceSha256(),converted.policy(),engine.orElseThrow().revision(),
                converted.dictionary(),configuration.minimumSupport()) : null;
        return result(blockers.isEmpty()?State.READY_FOR_MINING:State.BLOCKED,converted,metadata,size,proof,engine,blockers,input);
    }
    private static void validateMetadata(String id,Metadata m) {
        if (!id.equals(m.id()) || !hash(m.rawHash()) || m.rawSize()==null || !m.rawSize().matches("[1-9][0-9]*")
                || m.createdAt()==null || m.completeReport()==null) throw error("METADATA_INCONSISTENT");
        if (!Set.of("PENDING","VALID").contains(Objects.toString(m.validationStatus(),""))) throw error("SNAPSHOT_STATUS_UNSUPPORTED");
        if (!"wdm-order-csv-v1".equals(m.contractVersion()) || !"ordered_at".equals(m.timeField())
                || !"COMPLETED_CURRENT_AT_ACQUISITION".equals(m.statusPolicy())) throw error("SOURCE_METADATA_UNSUPPORTED");
        if (m.rawReference()==null) throw error("RAW_NOT_FOUND");
        if (!(id+"/raw.csv").equals(m.rawReference())) throw error("RAW_REFERENCE_INVALID");
        if ("CSV".equals(m.sourceType()) && "CSV_UPLOAD".equals(m.acquisition())) {
            if (m.fromTime()!=null || m.toTime()!=null || m.sourceTimeZone()!=null) throw error("SOURCE_METADATA_INCONSISTENT");
        } else if ("DATABASE".equals(m.sourceType()) && "DATABASE_EXPORT".equals(m.acquisition())) {
            // Acquisition provenance cannot be attested here; server SourceVerifier must verify ownership separately.
            if (!"UTC".equals(m.sourceTimeZone()) || m.fromTime()==null || m.toTime()==null
                    || !m.fromTime().isBefore(m.toTime())) throw error("SOURCE_METADATA_INCONSISTENT");
        } else throw error("SOURCE_METADATA_UNSUPPORTED");
    }
    private static void verifyConverted(ConvertedOrderSnapshot c,Metadata m) {
        if (!c.rawSha256().equals(m.rawHash()) || !OrderSnapshotConverter.POLICY.equals(c.policy())
                || !PreparedSnapshotMiner.sha256(c.spmf()).equals(c.sequenceSha256())
                || !PreparedSnapshotMiner.sha256(c.mapping()).equals(c.mappingSha256())) throw error("CONVERSION_INCONSISTENT");
        try {
            var db=new SpmfSequenceParser().parse(new StringReader(new String(c.spmf(),StandardCharsets.UTF_8)));
            var actual=new ItemStatisticsCalculator().calculate(db);
            if (actual.numberOfSequences()!=c.statistics().sequenceCount()
                    || actual.totalDatabaseLength()!=c.statistics().totalItemOccurrences()
                    || actual.numberOfDistinctItems()!=c.statistics().distinctItemCount()
                    || c.items().size()!=actual.numberOfDistinctItems()
                    || c.statistics().items().size()!=actual.numberOfDistinctItems()
                    || c.sequences().size()!=actual.numberOfSequences()
                    || c.statistics().rawRowCount()-actual.totalDatabaseLength()!=c.statistics().collapsedPresenceCount()
                    || !actual.itemStatistics().keySet().equals(c.dictionary().keySet())) throw error("CONVERSION_INCONSISTENT");
            long itemsets=0, min=Long.MAX_VALUE, max=0; int single=0;
            for (var record:db.records()) {
                var groups=record.sequence().itemsets();
                itemsets+=groups.size(); if (groups.size()==1) single++;
                long length=0; for (var group:groups) length+=group.size();
                min=Math.min(min,length); max=Math.max(max,length);
            }
            boolean empty=actual.numberOfSequences()==0;
            if (itemsets!=c.statistics().itemsetCount() || single!=c.statistics().singleItemsetSequenceCount()
                    || actual.numberOfSequences()-single!=c.statistics().multiItemsetSequenceCount()
                    || !Objects.equals(empty?null:min,c.statistics().minSequenceLength())
                    || !Objects.equals(empty?null:max,c.statistics().maxSequenceLength())
                    || empty!=(c.outcome()==ConvertedOrderSnapshot.Outcome.EMPTY)) throw error("CONVERSION_INCONSISTENT");
            for (var item:c.statistics().items()) {
                var stat=actual.itemStatistics().get(item.itemId());
                if (stat==null || stat.occurrenceCount()!=item.occurrenceCount()
                        || stat.sequenceSupport()!=item.sequenceSupport()) throw error("CONVERSION_INCONSISTENT");
            }
            if ("DATABASE".equals(m.sourceType())) {
                for (var seq:c.sequences()) for (var order:seq.orders()) {
                    var time=LocalDateTime.parse(order.orderedAt().substring(0,order.orderedAt().length()-1));
                    if (time.isBefore(m.fromTime()) || !time.isBefore(m.toTime())) throw error("EXPORT_SCOPE_MISMATCH");
                }
            }
        } catch (IOException | vn.edu.wspm.io.SpmfParseException bad) { throw error("CONVERSION_INCONSISTENT"); }
    }
    private static Handoff result(State state,ConvertedOrderSnapshot c,Metadata m,long size,Optional<SourceProof> source,
                                  Optional<EngineProof> engine,List<String> blockers,PreparedSnapshotMiner.Input input) {
        log.debug("Snapshot prepared in memory: state={}, sequences={}, blockers={}",state,c.statistics().sequenceCount(),blockers.size());
        return new Handoff(state,c,new Provenance(m.id(),m.sourceType(),m.acquisition(),m.sourceTimeZone(),m.fromTime(),m.toTime(),
                m.rawHash(),size,m.binding(),source,engine),blockers,input);
    }
    private static boolean text(String s,int max) { return s!=null && !s.isBlank() && s.indexOf('\0')<0
            && s.codePointCount(0,s.length())<=max && StandardCharsets.UTF_8.newEncoder().canEncode(s); }
    private static boolean hash(String s) { return s!=null && s.matches("[0-9a-f]{64}"); }
    private static ProviderException error(String code) { return new ProviderException(code,null); }
}
