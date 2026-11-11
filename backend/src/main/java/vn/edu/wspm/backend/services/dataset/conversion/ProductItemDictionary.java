package vn.edu.wspm.backend.services.dataset.conversion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Snapshot-local view of global IDs, in ascending numeric item order. No historical display text. */
public final class ProductItemDictionary {
    public static final String POLICY_VERSION = "GLOBAL_PRODUCT_ITEM_INT_V1";
    private final Map<String, Integer> productToItem;
    private final Map<Integer, String> itemToProduct;

    public ProductItemDictionary(Map<String, Integer> mappings) {
        var reverse = new TreeMap<Integer, String>();
        mappings.forEach((product, item) -> {
            if (product == null || product.isBlank() || item == null || item <= 0
                    || reverse.putIfAbsent(item, product) != null) {
                throw new IllegalArgumentException("Invalid product/item dictionary");
            }
        });
        var forward = new LinkedHashMap<String, Integer>();
        reverse.forEach((item, product) -> forward.put(product, item));
        productToItem = Collections.unmodifiableMap(forward);
        itemToProduct = Collections.unmodifiableMap(new LinkedHashMap<>(reverse));
    }

    public Map<String, Integer> productToItem() { return productToItem; }
    public Map<Integer, String> itemToProduct() { return itemToProduct; }
    @Override public String toString() { return "ProductItemDictionary[size=" + productToItem.size() + "]"; }
}
