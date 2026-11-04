package vn.edu.wspm.backend.repositories;

import java.math.BigInteger;
import java.util.List;
import vn.edu.wspm.backend.dto.pattern.PatternListResponse.Summary;

/** Run, count and page must be read in one consistent database snapshot. */
public interface PatternListReader {
    Snapshot read(String runId, long offset, int size);

    record Snapshot(String status, BigInteger publishedCount, BigInteger actualCount,
                    List<Summary> items) {
        public Snapshot { items = List.copyOf(items); }
    }
}
