package vn.edu.wspm.backend.services.combo.generation;

import java.util.*;

/** Required categories are ALL predicates over original source Pattern products. */
public record CategorySelection(List<RequiredCategory> requiredCategories,int minDistinctProducts,int maxDistinctProducts) {
    public static final String VERSION="TV2_SELECTED_CATEGORIES_V3";
    public static final String SOURCE="OWNER_20261108_CATEGORY_SELECTION";
    public record RequiredCategory(String categoryId,long quantity) {}
    public CategorySelection {
        if(requiredCategories==null||requiredCategories.isEmpty()||minDistinctProducts<2||maxDistinctProducts<minDistinctProducts
                ||maxDistinctProducts>ProductSelection.MAX_PRODUCTS||requiredCategories.size()>maxDistinctProducts)throw new IllegalArgumentException("INVALID_CATEGORY_SELECTION");
        var seen=new HashSet<String>();
        for(var item:requiredCategories)if(item==null||item.categoryId()==null||item.categoryId().isBlank()
                ||item.categoryId().codePointCount(0,item.categoryId().length())>40||item.categoryId().indexOf('\0')>=0
                ||!java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(item.categoryId())||!seen.add(item.categoryId())
                ||item.quantity()<1||item.quantity()>ProductSelection.MAX_QUANTITY)throw new IllegalArgumentException("INVALID_REQUIRED_CATEGORY");
        requiredCategories=requiredCategories.stream().sorted(Comparator.comparing(RequiredCategory::categoryId)).toList();
    }
    public String policyReference(){
        return SOURCE+":"+NativeComboGeneration.hash(vn.edu.wspm.backend.services.combo.persistence.BatchCanonical.encode(this,8*1024*1024));
    }
}
