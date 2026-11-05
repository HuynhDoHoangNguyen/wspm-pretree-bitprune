package vn.edu.wspm.backend.repositories;

import java.util.List;
import vn.edu.wspm.backend.dto.combo.ComboAdminRead.*;

/** Internal only. Caller must authorize an Admin before using this boundary. */
public interface ComboAdminReader {
    record Bundle(Row row,List<Item> items,List<Source> sources) {
        public Bundle { items=List.copyOf(items);sources=List.copyOf(sources); }
    }
    record Snapshot(boolean batchExists,String scopeTotal,String filteredTotal,List<Bundle> bundles) {
        public Snapshot { bundles=List.copyOf(bundles); }
    }
    Snapshot list(Filters filters,long offset,int size);
    Bundle detail(String comboId);
}
