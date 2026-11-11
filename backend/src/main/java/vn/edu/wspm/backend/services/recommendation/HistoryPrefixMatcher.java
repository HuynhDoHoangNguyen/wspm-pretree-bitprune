package vn.edu.wspm.backend.services.recommendation;

import java.util.*;
import vn.edu.wspm.backend.repositories.recommendation.ComboReadRepository.HistoryRow;
import vn.edu.wspm.backend.repositories.recommendation.ComboReadRepository.ItemRow;

/** Application policy, not a mining support verifier. See client-combo-contract.md. */
public final class HistoryPrefixMatcher {
    public static final String POLICY = "HISTORY_PREFIX_V1";
    public record Match(int matchedItemsets, long nextItemsetOrder, List<String> suggestedProductIds) {
        public Match { suggestedProductIds = List.copyOf(suggestedProductIds); }
    }

    public Optional<Match> match(List<ItemRow> items, List<HistoryRow> history) {
        var steps = new TreeMap<Long, Set<String>>();
        items.forEach(i -> steps.computeIfAbsent(i.itemsetOrder(), k -> new HashSet<>()).add(i.productId()));
        if (steps.size() < 2) return Optional.empty();
        var orders = new LinkedHashMap<String, Set<String>>();
        // SQL orders these rows by accepted chronology; retain separate exact order IDs.
        history.forEach(h -> orders.computeIfAbsent(h.orderId(), k -> new HashSet<>()).add(h.productId()));
        var bought = new HashSet<String>();
        orders.values().forEach(bought::addAll);
        var sequence = new ArrayList<>(orders.values());
        int cursor = 0, matched = 0;
        for (var step : steps.entrySet()) {
            while (cursor < sequence.size() && !sequence.get(cursor).containsAll(step.getValue())) cursor++;
            if (cursor == sequence.size()) {
                if (matched == 0) return Optional.empty();
                var suggestions = items.stream().filter(i -> i.itemsetOrder() == step.getKey())
                        .map(ItemRow::productId).filter(id -> !bought.contains(id)).distinct().toList();
                return suggestions.isEmpty() ? Optional.empty()
                        : Optional.of(new Match(matched, step.getKey(), suggestions));
            }
            matched++; cursor++;
        }
        return Optional.empty();
    }
}
