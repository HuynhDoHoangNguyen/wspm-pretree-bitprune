package vn.edu.wspm.backend.services.combo.generation;

import java.util.*;

/** Owner-requested catalog-only selection. Quantity is commercial, never mining frequency. */
public record ProductSelection(List<RequiredItem> requiredItems,int minDistinctProducts,int maxDistinctProducts) {
    public static final String VERSION="TV2_SELECTED_CATALOG_V2";
    public static final String SOURCE="OWNER_20261108_5c78aff9";
    // Existing atomic writer budget is 4000 item rows; a single Combo cannot exceed that budget.
    public static final int MAX_PRODUCTS=4000;
    public static final long MAX_QUANTITY=4294967295L; // COMBO_ITEM.quantity INT UNSIGNED
    public record RequiredItem(long itemId,long quantity) {}
    public static boolean flexiblePolicy(String version){return VERSION.equals(version)||CategorySelection.VERSION.equals(version);}
    /** Server-owned policy scope; not a client approval or an independent attestation. */
    public String policyReference(){
        byte[] bytes=vn.edu.wspm.backend.services.combo.persistence.BatchCanonical.encode(this,8*1024*1024);
        return SOURCE+":"+NativeComboGeneration.hash(bytes);
    }
    public ProductSelection {
        if(requiredItems==null||requiredItems.isEmpty()||minDistinctProducts<2||maxDistinctProducts<minDistinctProducts
                ||maxDistinctProducts>MAX_PRODUCTS||requiredItems.size()>maxDistinctProducts)throw new IllegalArgumentException("INVALID_PRODUCT_SELECTION");
        var seen=new HashSet<Long>();
        for(var item:requiredItems)if(item==null||item.itemId()<1||item.itemId()>Integer.MAX_VALUE
                ||item.quantity()<1||item.quantity()>MAX_QUANTITY||!seen.add(item.itemId()))throw new IllegalArgumentException("INVALID_REQUIRED_ITEM");
        requiredItems=requiredItems.stream().sorted(Comparator.comparingLong(RequiredItem::itemId)).toList();
    }
}
