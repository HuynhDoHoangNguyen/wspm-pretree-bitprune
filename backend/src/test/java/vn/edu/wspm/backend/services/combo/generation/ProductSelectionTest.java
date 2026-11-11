package vn.edu.wspm.backend.services.combo.generation;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class ProductSelectionTest {
    private ProductSelection.RequiredItem item(long id,long quantity){return new ProductSelection.RequiredItem(id,quantity);}
    @Test void normalizesOrderAndRetainsExactUnsignedQuantityAndDefensiveList(){
        var list=new ArrayList<>(List.of(item(27,4294967295L),item(12,2)));
        var selection=new ProductSelection(list,2,4000);list.clear();
        assertEquals(12,selection.requiredItems().getFirst().itemId());assertEquals(4294967295L,selection.requiredItems().getLast().quantity());
        assertThrows(UnsupportedOperationException.class,()->selection.requiredItems().clear());
        assertEquals(selection.policyReference(),new ProductSelection(List.of(item(12,2),item(27,4294967295L)),2,4000).policyReference());
        assertNotEquals(selection.policyReference(),new ProductSelection(List.of(item(12,3),item(27,4294967295L)),2,4000).policyReference());
    }
    @Test void rejectsEmptyDuplicatesBoundsAndInvalidQuantity(){
        assertThrows(IllegalArgumentException.class,()->new ProductSelection(List.of(),2,4));
        assertThrows(IllegalArgumentException.class,()->new ProductSelection(List.of(item(1,1),item(1,2)),2,4));
        for(long q:List.of(0L,-1L,4294967296L))assertThrows(IllegalArgumentException.class,()->new ProductSelection(List.of(item(1,q)),2,4));
        for(int[] bounds:List.of(new int[]{1,4},new int[]{4,2},new int[]{2,4001}))assertThrows(IllegalArgumentException.class,()->new ProductSelection(List.of(item(1,1)),bounds[0],bounds[1]));
        assertThrows(IllegalArgumentException.class,()->new ProductSelection(List.of(item(1,1),item(2,1),item(3,1)),2,2));
    }
}
