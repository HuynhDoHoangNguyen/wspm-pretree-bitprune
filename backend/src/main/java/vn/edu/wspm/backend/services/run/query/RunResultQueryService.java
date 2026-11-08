package vn.edu.wspm.backend.services.run.query;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.edu.wspm.backend.dto.run.result.RunResultResponse.*;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.repositories.run.RunResultQueryRepository;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec;
import vn.edu.wspm.backend.services.run.persistence.PatternWeightPolicy;
import vn.edu.wspm.backend.services.run.persistence.RunProvenance;
import vn.edu.wspm.backend.services.workspace.MiningWorkspaceReadService;
import vn.edu.wspm.model.Pattern;

/** Independent DB reader: never accesses a live Outcome, repairs storage or publishes results. */
@Service
@Slf4j
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
public class RunResultQueryService {
    public static final int MAX_PATTERNS = 10_000, MAX_TEMPORAL_ROWS = 100_000;
    private static final BigInteger UINT64_MAX = new BigInteger("18446744073709551615");
    private final RunResultQueryRepository repository;
    private final ObjectMapper json;
    private final int maxExportBytes;
    private final LongSupplier nanoTime;
    private vn.edu.wspm.backend.repositories.run.RunResultPublicationRepository publications;
    @Autowired
    public void publications(vn.edu.wspm.backend.repositories.run.RunResultPublicationRepository publications) { this.publications = publications; }
    @Autowired
    public RunResultQueryService(RunResultQueryRepository repository, ObjectMapper json,
            @Value("${wdm.result-query.max-export-bytes:16777216}") int maxExportBytes) {
        this(repository, json, maxExportBytes, System::nanoTime);
    }
    RunResultQueryService(RunResultQueryRepository repository, ObjectMapper json, int maxExportBytes, LongSupplier nanoTime) {
        if (maxExportBytes < 256 || maxExportBytes > 64 * 1024 * 1024) throw new IllegalArgumentException("Invalid result export budget");
        this.repository = repository; this.json = json; this.maxExportBytes = maxExportBytes; this.nanoTime = nanoTime;
    }
    private record Context(Metadata metadata, long sequences, double meanW) {}

    public Metadata metadata(String runId) { return context(runId).metadata(); }

    private Context context(String runId) {
        MiningWorkspaceReadService.id(runId);
        var row = query(runId, () -> repository.metadata(runId));
        if (row == null) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        String version = text(row, "provenance_version"), status = text(row, "publication_status");
        if ((version == null) != (status == null)) throw conflict();
        Provenance publicProvenance = null; long sequences = 0;
        if (version != null) {
            RunProvenance provenance;
            try { provenance = json.readValue(text(row, "document"), RunProvenance.class); }
            catch (RuntimeException malformed) { throw conflict(); }
            if (provenance == null || !version.equals(provenance.version())) throw conflict();
            var p = provenance.binding().publication();
            if (!runId.equals(text(row, "run_id")) || !p.snapshotId().equals(text(row, "snapshot_id"))) throw conflict();
            if (!Set.of("NOT_PUBLISHED", "COMMITTED").contains(status)) throw conflict();
            sequences = p.statistics().sequenceCount();
            publicProvenance = new Provenance(version, provenance.conversionFormatVersion(), p.snapshotId(), p.attemptId(),
                    p.validationSha256(), p.sequence().sha256(), p.mapping().sha256(), Long.toString(sequences));
        } else status = "UNVERIFIED";
        var counts = query(runId, () -> repository.counts(runId));
        String execution = text(row, "status");
        boolean staging = "NOT_PUBLISHED".equals(status) && "SUCCEEDED".equals(execution);
        double mean = row.get("mean_w") instanceof Number n ? n.doubleValue() : Double.NaN;
        if (version != null && (!Double.isFinite(mean) || mean <= 0 || mean > 1)) throw conflict();
        boolean published = false;
        if ("COMMITTED".equals(status) && vn.edu.wspm.backend.services.run.persistence.ResultManifestCodec.VERSION.equals(text(row,"manifest_version"))) {
            if (publications == null) throw conflict();
            query(runId, () -> publications.verified(runId));
            published = true;
        }
        var metadata = new Metadata(runId, execution, status, text(row, "manifest_version"), text(row, "result_sha256"),
                row.get("committed_at") == null ? null : Instant.parse(text(row, "committed_at")), publicProvenance,
                counts.patterns(), counts.temporal(), counts.statistics(), published ? "COMPLETE_VERIFIED" : "UNVERIFIED", staging, published, published && !"0".equals(counts.patterns()));
        log.debug("Run result metadata read: runId={}, publication={}", runId, status);
        return new Context(metadata, sequences, mean);
    }
    private Context readable(String runId, String view) {
        if (view == null) view = "published";
        if (!Set.of("staging", "published").contains(view)) throw ApplicationException.invalid("view", "Use staging or published.");
        var context = context(runId);
        if ("staging".equals(view) ? !context.metadata().stagingReadable() : !context.metadata().publishedReadable()) throw conflict();
        return context;
    }
    public Page page(String runId, String view, String page, String size) {
        int p = MiningWorkspaceReadService.integer(page, 1, 1, 100_000);
        int s = MiningWorkspaceReadService.integer(size, 20, 1, 100);
        var context = readable(runId, view);
        var rows = query(runId, () -> repository.page(runId, (p - 1) * s, s));
        rows.forEach(row -> validate(row, context));
        return new Page(context.metadata(), List.copyOf(rows), p, s, context.metadata().storedPatternCount());
    }
    public PatternDetail detail(String runId, String patternId, String view) {
        decimalId(patternId);
        var context = readable(runId, view);
        var parent = query(runId, () -> repository.pattern(runId, patternId));
        if (parent == null) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        return detail(context, parent);
    }
    private PatternDetail detail(Context context, PatternSummary parent) {
        validate(parent, context);
        var rows = query(parent.runId(), () -> repository.temporal(parent.runId(), parent.patternId(), MAX_TEMPORAL_ROWS + 1));
        if (rows.size() != parent.patternItemCount() || rows.isEmpty() || rows.size() > MAX_TEMPORAL_ROWS) throw conflict();
        var itemsets = new ArrayList<Itemset>(); var members = new ArrayList<Item>();
        var coreSets = new ArrayList<vn.edu.wspm.model.Itemset>(); var coreItems = new ArrayList<vn.edu.wspm.model.Item>();
        var weights = new HashMap<Integer, Double>();
        int currentSet = 1, nextItem = 1, previous = 0; long bytes = 0;
        for (var row : rows) {
            int set = integer(row, "set_order"), order = integer(row, "item_order"), id = integer(row, "item_id");
            if (set != currentSet) {
                if (set != currentSet + 1 || members.isEmpty()) throw conflict();
                itemsets.add(new Itemset(currentSet, List.copyOf(members))); coreSets.add(new vn.edu.wspm.model.Itemset(coreItems));
                members = new ArrayList<>(); coreItems = new ArrayList<>(); currentSet++; nextItem = 1; previous = 0;
            }
            if (order != nextItem++ || id <= previous) throw conflict(); previous = id;
            String product = text(row, "product_id"), name = text(row, "product_name_snapshot"), sku = text(row, "sku_snapshot");
            if (product == null || product.isBlank() || name == null || sku == null) throw conflict();
            double weight = number(row, "item_weight");
            if (weight <= 0 || weight > 1) throw conflict();
            String occurrences = text(row, "occurrences"), support = text(row, "support");
            var supportValue = unsigned(support, false);
            if (supportValue.compareTo(BigInteger.valueOf(context.sequences())) > 0
                    || unsigned(occurrences, false).compareTo(supportValue) < 0) throw conflict();
            bytes += 4L * (product.length() + name.length() + sku.length()) + 256;
            if (bytes > maxExportBytes) throw tooLarge();
            members.add(new Item(order, id, product, name, sku, weight, occurrences, support));
            coreItems.add(new vn.edu.wspm.model.Item(id)); weights.put(id, weight);
        }
        itemsets.add(new Itemset(currentSet, List.copyOf(members))); coreSets.add(new vn.edu.wspm.model.Itemset(coreItems));
        var pattern = new Pattern(coreSets);
        if (itemsets.size() != parent.itemsetCount() || !pattern.serialize().equals(parent.canonicalPattern())
                || Double.doubleToLongBits(PatternWeightPolicy.calculate(pattern, weights)) != Double.doubleToLongBits(parent.patternWeight())) throw conflict();
        return new PatternDetail(context.metadata(), parent, List.copyOf(itemsets));
    }
    private void validate(PatternSummary row, Context context) {
        decimalStoredId(row.patternId());
        if (!context.metadata().runId().equals(row.runId()) || row.canonicalPattern() == null
                || row.canonicalPattern().getBytes(StandardCharsets.UTF_8).length > 65_535
                || !ConversionArtifactCodec.sha256(row.canonicalPattern().getBytes(StandardCharsets.UTF_8)).equals(row.canonicalPatternHash())
                || !PatternWeightPolicy.VERSION.equals(row.patternWeightPolicy())
                || row.itemsetCount() <= 0 || row.patternItemCount() < row.itemsetCount() || row.patternItemCount() > MAX_TEMPORAL_ROWS
                || !Double.isFinite(row.patternWeight()) || row.patternWeight() <= 0 || row.patternWeight() > 1) throw conflict();
        var support = unsigned(row.supportAbs(), false);
        if (support.compareTo(BigInteger.valueOf(context.sequences())) > 0) throw conflict();
        double value = support.doubleValue();
        if (Double.doubleToLongBits(row.supportRatio()) != Double.doubleToLongBits(value / context.sequences())
                || Double.doubleToLongBits(row.weightedSupport()) != Double.doubleToLongBits(value * context.meanW())) throw conflict();
    }
    /** JSON is fully validated and bounded before HTTP headers/body are committed. */
    public byte[] export(String runId, String view) {
        if (view == null || "published".equals(view)) {
            var bundle = publishedBundle(runId);
            var out = new ByteArrayOutputStream();
            append(out, json.writeValueAsBytes(new PublishedExport("WDM_RESULT_EXPORT_V2", "COMBO_HANDOFF", bundle.result(), bundle.patterns(), bundle.runItems())));
            return out.toByteArray();
        }
        long deadline = nanoTime.getAsLong() + 10_000_000_000L;
        var context = readable(runId, view);
        if (unsigned(context.metadata().storedPatternCount(), true).compareTo(BigInteger.valueOf(MAX_PATTERNS)) > 0
                || unsigned(context.metadata().storedPatternItemCount(), true).compareTo(BigInteger.valueOf(MAX_TEMPORAL_ROWS)) > 0) throw tooLarge();
        var out = new ByteArrayOutputStream();
        append(out, "{\"formatVersion\":\"WDM_RESULT_EXPORT_V1\",\"purpose\":\"STAGING_INSPECTION\",\"result\":".getBytes(StandardCharsets.UTF_8));
        append(out, json.writeValueAsBytes(context.metadata())); append(out, ",\"patterns\":[".getBytes(StandardCharsets.UTF_8));
        int count = 0;
        for (int offset = 0; ; offset += 100) {
            checkDeadline(deadline);
            int batchOffset = offset;
            var page = query(runId, () -> repository.page(runId, batchOffset, 100));
            for (var parent : page) {
                checkDeadline(deadline);
                if (count++ > 0) append(out, new byte[]{','});
                var detail = detail(context, parent);
                append(out, json.writeValueAsBytes(new ExportPattern(detail.pattern(), detail.itemsets())));
            }
            if (page.size() < 100) break;
        }
        if (!Integer.toString(count).equals(context.metadata().storedPatternCount())) throw conflict();
        append(out, new byte[]{']', '}'}); checkDeadline(deadline);
        log.info("Run staging export completed: runId={}, patterns={}, bytes={}", runId, count, out.size());
        return out.toByteArray();
    }
    /** Complete immutable consumer payload; no live engine, source files or current catalog access. */
    public PublishedBundle publishedBundle(String runId) {
        long deadline = nanoTime.getAsLong() + 10_000_000_000L;
        var context = readable(runId, "published");
        var snapshot = query(runId, () -> publications.verified(runId));
        var patterns = new ArrayList<ExportPattern>();
        for (int offset = 0; offset < snapshot.patterns().size(); offset += 100) {
            checkDeadline(deadline); int next = offset;
            for (var parent : query(runId, () -> repository.page(runId, next, 100))) {
                checkDeadline(deadline);
                var d = detail(context, parent); patterns.add(new ExportPattern(d.pattern(), d.itemsets()));
            }
        }
        if (patterns.size() != snapshot.patterns().size()) throw conflict();
        var items = new ArrayList<RunItem>();
        for (var row : snapshot.items()) {
            int id = integer(row,"item_id");
            String product=text(row,"product_id"), name=text(row,"product_name_snapshot"), sku=text(row,"sku_snapshot");
            String occurrences=text(row,"occurrences"), support=text(row,"support");
            var sup=unsigned(support,false); double weight=number(row,"item_weight");
            if (product==null || product.isBlank() || name==null || sku==null || weight<=0 || weight>1
                    || sup.compareTo(BigInteger.valueOf(context.sequences()))>0 || unsigned(occurrences,false).compareTo(sup)<0) throw conflict();
            items.add(new RunItem(id,product,name,sku,weight,occurrences,support));
        }
        checkDeadline(deadline);
        return new PublishedBundle(context.metadata(),List.copyOf(patterns),List.copyOf(items));
    }
    private void append(ByteArrayOutputStream out, byte[] bytes) {
        if ((long) out.size() + bytes.length > maxExportBytes) throw tooLarge();
        out.writeBytes(bytes);
    }
    private <T> T query(String runId, Supplier<T> operation) {
        try { return operation.get(); }
        catch (DataAccessException | org.springframework.transaction.TransactionTimedOutException failure) {
            // Preserve the diagnostic stack without SQL messages, causes or bound values.
            var safe = new IllegalStateException("Run result query failed: " + failure.getClass().getSimpleName());
            safe.setStackTrace(failure.getStackTrace());
            log.error("Run result query unavailable: runId={}", runId, safe);
            throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE, "Stored result is temporarily unavailable.");
        }
    }
    private void checkDeadline(long deadline) {
        if (nanoTime.getAsLong() - deadline >= 0) throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE, "Result export exceeded its time budget.");
    }
    public static String decimalId(String value) {
        try { unsigned(value, false); return value; }
        catch (ApplicationException invalid) { throw ApplicationException.invalid("patternId", "Supply a positive canonical unsigned BIGINT."); }
    }
    private static void decimalStoredId(String value) { unsigned(value, false); }
    private static BigInteger unsigned(String value, boolean zero) {
        if (value == null || !value.matches(zero ? "0|[1-9][0-9]{0,19}" : "[1-9][0-9]{0,19}")) throw conflict();
        var n = new BigInteger(value); if (n.compareTo(UINT64_MAX) > 0) throw conflict(); return n;
    }
    private static int integer(Map<String, Object> row, String key) { return RunResultQueryRepository.supportedInt(text(row, key)); }
    private static String text(Map<String, Object> row, String key) { return (String) row.get(key); }
    private static double number(Map<String, Object> row, String key) {
        if (!(row.get(key) instanceof Number n) || !Double.isFinite(n.doubleValue())) throw conflict(); return n.doubleValue();
    }
    private static ApplicationException conflict() {
        log.warn("Run result read rejected: unsupported visibility or inconsistent stored result");
        return new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE, "Result visibility, version or stored structure is unsupported or inconsistent.");
    }
    private static ApplicationException tooLarge() {
        return new ApplicationException(ErrorCode.PAYLOAD_TOO_LARGE, "Result read exceeds the supported row or byte budget.");
    }
}
