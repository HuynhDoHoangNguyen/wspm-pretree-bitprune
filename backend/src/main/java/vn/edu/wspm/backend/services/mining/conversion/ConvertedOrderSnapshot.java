package vn.edu.wspm.backend.services.mining.conversion;

import java.util.*;
import vn.edu.wspm.backend.services.mining.PreparedSnapshotMiner.DictionaryItem;

/** Unpublished conversion facts only; contains no run identity or completion/approval evidence. */
public record ConvertedOrderSnapshot(
        String policy, Outcome outcome, String rawSha256, byte[] spmf, String sequenceSha256,
        byte[] mapping, String mappingSha256, List<Item> items, List<CustomerSequence> sequences,
        Statistics statistics) {
    public enum Outcome { CONVERTED, EMPTY }
    public record Item(int itemId, String productId, String nameSnapshot, String skuSnapshot,
                       String labelSourceLineId, int labelVariantCount) {}
    public record Order(String orderId, String orderedAt) {}
    public record CustomerSequence(int sid, String customerCode, List<Order> orders) {
        public CustomerSequence { orders = List.copyOf(orders); }
    }
    public record ItemStatistic(int itemId, long occurrenceCount, int sequenceSupport) {}
    /** Average is the exact ratio totalItemOccurrences/sequenceCount; empty has no min/max. */
    public record Statistics(int rawRowCount, long itemsetCount, int sequenceCount, int distinctItemCount,
                             long totalItemOccurrences, long collapsedPresenceCount,
                             int singleItemsetSequenceCount, int multiItemsetSequenceCount,
                             Long minSequenceLength, Long maxSequenceLength,
                             List<ItemStatistic> items) {
        public Statistics { items = List.copyOf(items); }
    }
    public ConvertedOrderSnapshot {
        spmf = spmf.clone(); mapping = mapping.clone();
        items = List.copyOf(items); sequences = List.copyOf(sequences);
        Objects.requireNonNull(statistics);
    }
    @Override public byte[] spmf() { return spmf.clone(); }
    @Override public byte[] mapping() { return mapping.clone(); }
    public Map<Integer, DictionaryItem> dictionary() {
        var result = new TreeMap<Integer, DictionaryItem>();
        for (var item : items) result.put(item.itemId(),
                new DictionaryItem(item.productId(), item.nameSnapshot(), item.skuSnapshot()));
        return Collections.unmodifiableMap(result);
    }
    @Override public String toString() {
        return "ConvertedOrderSnapshot[outcome=" + outcome + ", rows=" + statistics.rawRowCount() + "]";
    }
}
