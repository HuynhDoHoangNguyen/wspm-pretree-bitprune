package vn.edu.wspm.backend.repositories;

import java.util.List;
import vn.edu.wspm.backend.dto.run.RunListResponse.Summary;

public interface RunListReader {
    record Snapshot(String total, List<Summary> items) {
        public Snapshot { items = List.copyOf(items); }
    }
    Snapshot read(long offset, int size);
}
