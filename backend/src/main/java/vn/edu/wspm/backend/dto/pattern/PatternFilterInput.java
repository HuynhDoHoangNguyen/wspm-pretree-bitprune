package vn.edu.wspm.backend.dto.pattern;

import java.math.BigInteger;
import java.util.List;
import java.util.TreeSet;
import vn.edu.wspm.backend.services.pattern.PatternReadException;
import static vn.edu.wspm.backend.services.pattern.PatternReadException.Code.INVALID_FILTER;

/** Raw scalar strings retain invalid empty/exponent/fractional inputs for validation. */
public record PatternFilterInput(List<String> itemIds, String supportMin, String supportMax,
        String weightMin, String weightMax) {
    public static final PatternFilterInput EMPTY = new PatternFilterInput(List.of(), null,null,null,null);
    public PatternFilterInput {
        if (itemIds != null) itemIds = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(itemIds));
    }

    public Filter validated() {
        var ids = new TreeSet<Integer>();
        if (itemIds != null) {
            if (itemIds.size() > 100) throw invalid();
            for (String id : itemIds) {
                BigInteger value = integer(id);
                if (value == null || value.signum() <= 0 || value.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) throw invalid();
                ids.add(value.intValueExact());
            }
        }
        BigInteger min = integer(supportMin), max = integer(supportMax);
        Double wmin = weight(weightMin), wmax = weight(weightMax);
        if (min != null && max != null && min.compareTo(max) > 0
                || wmin != null && wmax != null && wmin > wmax) throw invalid();
        return new Filter(List.copyOf(ids), min,max,wmin,wmax);
    }
    private static BigInteger integer(String raw) {
        if (raw == null) return null;
        if (!raw.matches("[0-9]{1,19}")) throw invalid();
        var value = new BigInteger(raw);
        if (value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) throw invalid();
        return value;
    }
    private static Double weight(String raw) {
        if (raw == null) return null;
        if (raw.length() > 64 || !raw.matches("-?[0-9]+(?:\\.[0-9]+)?")) throw invalid();
        double value = Double.parseDouble(raw);
        if (!Double.isFinite(value)) throw invalid();
        return value;
    }
    private static PatternReadException invalid() { return new PatternReadException(INVALID_FILTER); }
    public record Filter(List<Integer> itemIds, BigInteger supportMin, BigInteger supportMax,
            Double weightMin, Double weightMax) {
        public Filter { itemIds=List.copyOf(itemIds); }
        public boolean hasWeight() { return weightMin != null || weightMax != null; }
        public boolean empty() { return itemIds.isEmpty() && supportMin==null && supportMax==null && !hasWeight(); }
    }
}
