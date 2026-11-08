package vn.edu.wspm.backend.services.combo.generation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NativeComboPolicyTest {
    @Test void exactWhitelistsAndOwnerRankingAreVersioned(){
        var policy=new NativeComboPolicy("laptop-id","keyboard-id,mouse-id,bag-id,speaker-id");
        assertTrue(policy.configured());assertFalse(policy.accessories.contains("cpu-id"));
        assertEquals("SUPPORT_THEN_PATTERN_WEIGHT_V1",policy.ranking(5).rankingPolicy());
        assertEquals("FIRST_ITEMSET_V1",policy.ranking(5).anchorPolicy());
        assertEquals("EQ2_EQ3_ARITHMETIC_MEAN_V1",policy.ranking(5).weightPolicy().policy());
        assertDoesNotThrow(()->policy.ranking(1));assertDoesNotThrow(()->policy.ranking(20));
        assertThrows(IllegalArgumentException.class,()->policy.ranking(0));assertThrows(IllegalArgumentException.class,()->policy.ranking(21));
    }
    @Test void unconfiguredOverlapAliasAndDuplicateCannotGrantPolicy(){
        assertFalse(new NativeComboPolicy("","").configured());
        assertThrows(IllegalArgumentException.class,()->new NativeComboPolicy("a","a"));
        assertThrows(IllegalArgumentException.class,()->new NativeComboPolicy("a","b,b"));
        assertThrows(IllegalArgumentException.class,()->new NativeComboPolicy("a"," b"));
    }
}
