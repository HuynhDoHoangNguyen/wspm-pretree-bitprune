package vn.edu.wspm.backend.services.combo.generation;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class CategorySelectionTest {
    @Test void validatesIdsQuantitiesAndCanonicalScope(){
        var a=new CategorySelection.RequiredCategory("A",2);var b=new CategorySelection.RequiredCategory("B",3);
        var selection=new CategorySelection(List.of(b,a),2,5);
        assertEquals(selection.policyReference(),new CategorySelection(List.of(a,b),2,5).policyReference());
        assertNotEquals(selection.policyReference(),new CategorySelection(List.of(new CategorySelection.RequiredCategory("A",3),b),2,5).policyReference());
        assertThrows(IllegalArgumentException.class,()->new CategorySelection(List.of(a,a),2,5));
        assertThrows(IllegalArgumentException.class,()->new CategorySelection(List.of(),2,5));
        assertThrows(IllegalArgumentException.class,()->new CategorySelection(List.of(new CategorySelection.RequiredCategory("A",0)),2,5));
        assertThrows(IllegalArgumentException.class,()->new CategorySelection(List.of(a),5,2));
    }
}
