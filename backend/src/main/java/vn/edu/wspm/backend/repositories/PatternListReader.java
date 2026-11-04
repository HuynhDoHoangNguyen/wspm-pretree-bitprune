package vn.edu.wspm.backend.repositories;

import java.math.BigInteger;
import java.util.List;
import vn.edu.wspm.backend.dto.pattern.PatternListResponse.Summary;
import vn.edu.wspm.backend.dto.pattern.PatternListResponse.WeightFilter;
import vn.edu.wspm.backend.dto.pattern.PatternFilterInput.Filter;

/** Run, count and page must be read in one consistent database snapshot. */
public interface PatternListReader {
    Snapshot read(String runId, long offset, int size);
    default Snapshot read(String runId,long offset,int size,Filter filter) {
        if (!filter.empty()) throw new UnsupportedOperationException("Filtered reader required");
        return read(runId,offset,size);
    }

    record Snapshot(String status, BigInteger publishedCount, BigInteger actualCount,
                    List<Summary> items, BigInteger filteredCount, WeightFilter weightFilter) {
        public Snapshot { items = List.copyOf(items); }
        public Snapshot(String status,BigInteger publishedCount,BigInteger actualCount,List<Summary> items) {
            this(status,publishedCount,actualCount,items,actualCount,
                    new WeightFilter(false,"POLICY_NOT_CONFIRMED",null));
        }
    }
}
