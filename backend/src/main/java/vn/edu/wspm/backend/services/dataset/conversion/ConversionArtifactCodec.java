package vn.edu.wspm.backend.services.dataset.conversion;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.services.dataset.validation.ValidationLimits;
import vn.edu.wspm.backend.services.dataset.validation.ValidationReport;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.model.SequenceDatabase;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactResult.Statistics;

/** Frozen mapping JSON, readback and descriptive sequence statistics; no mining weights/supports. */
@Component
public class ConversionArtifactCodec {
    public static final String VERSION = "CONVERSION_ARTIFACT_V1";
    public static final Map<String, String> POLICIES = Map.of("mapping", ProductItemDictionary.POLICY_VERSION,
            "grouping", GroupedDataset.POLICY_VERSION, "ordering", OrderedDataset.POLICY_VERSION,
            "spmf", SpmfDatasetSerializer.POLICY_VERSION);
    private final ObjectMapper mapper;
    private final ValidationLimits limits;
    public ConversionArtifactCodec(ObjectMapper mapper, ValidationLimits limits) { this.mapper = mapper; this.limits = limits; }
    public record Entry(int itemId, String productId) {}
    public record DictionaryDocument(String formatVersion, String policyVersion, List<Entry> items) {}
    public record Verified(Statistics statistics, SequenceDatabase database) {
        @Override public String toString() { return "VerifiedConversionArtifacts[sequences=" + statistics.sequenceCount() + "]"; }
    }

    public byte[] mappingBytes(ProductItemDictionary dictionary) {
        var entries = dictionary.itemToProduct().entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList();
        byte[] json = mapper.writeValueAsBytes(new DictionaryDocument(VERSION, ProductItemDictionary.POLICY_VERSION, entries));
        byte[] bytes = Arrays.copyOf(json, json.length + 1); bytes[json.length] = '\n';
        return bytes;
    }

    public Verified verify(byte[] spmf, byte[] mapping) {
        try {
            // Bound parser allocations before its whitespace tokenization, using the accepted kept-row budget.
            long tokens = 0; int width = 0;
            for (byte b : spmf) {
                if (b == ' ' || b == '\n') { if (width != 0) { tokens++; width = 0; } }
                else { if (b != '-' && (b < '0' || b > '9') || ++width > 11) throw invalid(); }
                if (tokens > 3L * limits.maxRows()) throw invalid();
            }
            if (width != 0 || tokens == 0) throw invalid(); // canonical serializer always ends in LF
            var doc = mapper.readValue(mapping, DictionaryDocument.class);
            if (doc == null || !VERSION.equals(doc.formatVersion()) || !ProductItemDictionary.POLICY_VERSION.equals(doc.policyVersion())
                    || doc.items() == null || doc.items().isEmpty() || doc.items().size() > limits.maxRows()) throw invalid();
            var products = new LinkedHashMap<String, Integer>();
            for (var entry : doc.items()) {
                if (entry == null || entry.productId() == null || entry.productId().isBlank()
                        || entry.productId().codePointCount(0, entry.productId().length()) > 40
                        || products.putIfAbsent(entry.productId(), entry.itemId()) != null) throw invalid();
            }
            var dictionary = new ProductItemDictionary(products);
            if (!Arrays.equals(mapping, mappingBytes(dictionary))) throw invalid();
            var db = new SpmfSequenceParser().parse(new InputStreamReader(new ByteArrayInputStream(spmf), StandardCharsets.UTF_8));
            var distinct = new HashSet<Integer>();
            long single = 0, multi = 0, itemsets = 0, occurrences = 0;
            int min = Integer.MAX_VALUE, max = 0;
            for (var record : db.records()) {
                if (record.sequence().size() == 1) single++; else multi++;
                long length = 0;
                for (var itemset : record.sequence().itemsets()) {
                    itemsets++; length += itemset.size();
                    itemset.items().forEach(item -> distinct.add(item.id()));
                }
                int exactLength = Math.toIntExact(length);
                occurrences += length; min = Math.min(min, exactLength); max = Math.max(max, exactLength);
            }
            if (db.size() == 0 || occurrences > limits.maxRows() || !distinct.equals(dictionary.itemToProduct().keySet())) throw invalid();
            return new Verified(new Statistics(db.size(), single, multi, itemsets, distinct.size(), occurrences,
                    min, max, (double) occurrences / db.size()), db);
        } catch (ApplicationException expected) { throw expected; }
        catch (Exception malformed) { throw invalid(); }
    }

    public void requireReadback(OrderedDataset expected, Verified actual) {
        if (expected.customers().size() != actual.database().size()) throw invalid();
        for (int i = 0; i < expected.customers().size(); i++) {
            var orders = expected.customers().get(i).orders(); var record = actual.database().records().get(i);
            if (record.sid() != i + 1 || orders.size() != record.sequence().size()) throw invalid();
            for (int j = 0; j < orders.size(); j++) {
                if (!orders.get(j).itemIds().equals(record.sequence().itemsets().get(j).items().stream()
                        .map(vn.edu.wspm.model.Item::id).toList())) throw invalid();
            }
        }
    }
    public String validationHash(ValidationReport report) { return sha256(mapper.writeValueAsBytes(report)); }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    public static ApplicationException invalid() {
        return new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE, "Conversion artifacts or binding are inconsistent.");
    }
}
