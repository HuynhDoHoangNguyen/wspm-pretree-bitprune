package vn.edu.wspm.backend.services.mining;

import java.io.StringReader;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.mining.ResultRetentionPolicy;
import vn.edu.wspm.mining.WspmMiningResult;
import vn.edu.wspm.mining.WspmPreTreeMiner;
import vn.edu.wspm.statistics.ItemStatisticsCalculator;

/** Engine boundary only. No bean/HTTP/provider/publisher or policy approval is registered. */
public final class PreparedSnapshotMiner {
    private static final Logger log=LoggerFactory.getLogger(PreparedSnapshotMiner.class);
    public record DictionaryItem(String productId,String nameSnapshot,String skuSnapshot) {}
    public record Input(String snapshotId,String rawSha256,byte[] spmf,String sequenceSha256,
                        String conversionPolicy,String engineRevision,Map<Integer,DictionaryItem> dictionary,
                        MinimumSupport minimumSupport) {
        public Input { spmf=spmf.clone();dictionary=Collections.unmodifiableMap(new TreeMap<>(dictionary));Objects.requireNonNull(minimumSupport); }
        @Override public byte[] spmf() { return spmf.clone(); }
        @Override public String toString() { return "PreparedSnapshotInput[redacted]"; }
    }
    public record Output(Input input,WspmMiningResult engine,Map<String,Integer> canonicalSupports,List<String> publishBlockers) {
        public Output { canonicalSupports=Collections.unmodifiableMap(new TreeMap<>(canonicalSupports));publishBlockers=List.copyOf(publishBlockers); }
        @Override public String toString() { return "UnpublishedMiningOutput[patterns="+canonicalSupports.size()+"]"; }
    }
    public record StoredPattern(String canonical,String supportAbs) {}

    public Output mine(Input input) {
        Objects.requireNonNull(input);
        if(!text(input.snapshotId(),40) || !hash(input.rawSha256()) || !hash(input.sequenceSha256())
                || !text(input.conversionPolicy(),80) || !text(input.engineRevision(),160)) throw invalid("INPUT_PROVENANCE_INVALID");
        byte[] bytes=input.spmf();
        if(!sha256(bytes).equals(input.sequenceSha256())) throw invalid("SEQUENCE_HASH_MISMATCH");
        final vn.edu.wspm.model.SequenceDatabase database;
        try {
            String decoded=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            database=new SpmfSequenceParser().parse(new StringReader(decoded));
        } catch(java.io.IOException | vn.edu.wspm.io.SpmfParseException failure) { throw invalid("SEQUENCE_FORMAT_INVALID"); }
        var statistics=new ItemStatisticsCalculator().calculate(database);
        if(!statistics.itemStatistics().keySet().equals(input.dictionary().keySet())) throw invalid("DICTIONARY_COVERAGE_MISMATCH");
        var products=new HashSet<String>();
        for(var entry:input.dictionary().entrySet()) {
            // V2 PATTERN_ITEM requires >0. Do not renumber core's otherwise-valid item0.
            if(entry.getKey()<=0) throw invalid("V2_ITEM_ID_DOMAIN_UNSUPPORTED");
            var item=entry.getValue();
            if(item==null || !text(item.productId(),40) || !text(item.nameSnapshot(),200) || !text(item.skuSnapshot(),80)
                    || !products.add(item.productId())) throw invalid("DICTIONARY_INVALID");
        }
        var resolved=new MinimumSupportResolver().resolve(input.minimumSupport(),database.size());
        if(!(resolved.effectiveThreshold()>0)) throw invalid("ZERO_EFFECTIVE_THRESHOLD_UNSUPPORTED");
        // Accepted baseline retention policy; no algorithm/formula/comparator reimplementation here.
        var engine=new WspmPreTreeMiner().mine(database,input.minimumSupport(),ResultRetentionPolicy.SUPPORT_ONLY);
        var canonical=new TreeMap<String,Integer>();
        engine.supports().forEach((pattern,support)->{
            if(canonical.put(pattern.serialize(),support)!=null) throw invalid("ENGINE_IDENTITY_CONFLICT");
        });
        if(canonical.size()!=engine.acceptedPatterns().size() || canonical.size()!=engine.statistics().patternCount())
            throw invalid("ENGINE_RESULT_INCONSISTENT");
        log.info("Prepared snapshot mined without publication: sequences={}, patterns={}",database.size(),canonical.size());
        // Refs/labels supplied by a caller do not constitute verified conversion/policy or durable completion evidence.
        return new Output(input,engine,canonical,List.of("CONVERTED_SNAPSHOT_PROVIDER_UNCONFIRMED",
                "PATTERN_WEIGHT_POLICY_UNCONFIRMED","PUBLISHER_PROTOCOL_UNIMPLEMENTED","PUBLISH_EVIDENCE_STORAGE_UNCONFIRMED"));
    }

    /** Future publisher must provide every stored row; count-only or duplicate-collapsing comparisons are insufficient. */
    public void verifyRoundTrip(Output output,List<StoredPattern> rows) {
        var actual=new TreeMap<String,BigInteger>();
        for(var row:rows) {
            if(row==null || row.canonical()==null || row.supportAbs()==null || !row.supportAbs().matches("[0-9]+")) throw invalid("STORED_RESULT_INVALID");
            if(actual.put(row.canonical(),new BigInteger(row.supportAbs()))!=null) throw invalid("STORED_IDENTITY_DUPLICATE");
        }
        var expected=new TreeMap<String,BigInteger>();
        output.canonicalSupports().forEach((key,support)->expected.put(key,BigInteger.valueOf(support)));
        if(!expected.equals(actual)) throw invalid("ROUND_TRIP_MISMATCH");
    }

    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static boolean hash(String value) { return value!=null && value.matches("[a-f0-9]{64}"); }
    private static boolean text(String value,int max) {
        return value!=null && !value.isBlank() && value.codePointCount(0,value.length())<=max
                && value.indexOf('\0')<0 && StandardCharsets.UTF_8.newEncoder().canEncode(value);
    }
    private static IllegalArgumentException invalid(String code) { return new IllegalArgumentException(code); }
}
