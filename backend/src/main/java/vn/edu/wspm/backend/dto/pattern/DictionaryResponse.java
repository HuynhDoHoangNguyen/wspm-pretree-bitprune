package vn.edu.wspm.backend.dto.pattern;

import java.util.List;
import vn.edu.wspm.backend.dto.pattern.PatternListResponse.ResultState;

public record DictionaryResponse(String runId,ResultState resultState,String query,int page,int size,
        String dictionaryTotal,String matchedTotal,List<Item> items) {
    public DictionaryResponse { items=List.copyOf(items); }
    public record Item(long itemId,String productId,String nameSnapshot,String skuSnapshot) { }
}
