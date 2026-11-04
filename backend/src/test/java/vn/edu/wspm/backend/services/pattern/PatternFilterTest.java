package vn.edu.wspm.backend.services.pattern;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.wspm.backend.dto.pattern.*;
import vn.edu.wspm.backend.repositories.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatternFilterTest {
    @Test void deduplicatesItemsAndPreservesIntegerBounds() {
        var f=new PatternFilterInput(List.of("2","1","2"),"9007199254740993",null,"-0.5","0.5").validated();
        assertEquals(List.of(1,2),f.itemIds());
        assertEquals(new BigInteger("9007199254740993"),f.supportMin());
        assertNull(f.supportMax());assertEquals(-.5,f.weightMin());assertTrue(f.hasWeight());
    }
    @ParameterizedTest @ValueSource(strings={"-1","1.5","1e3","","9223372036854775808","NaN"})
    void rejectsInvalidSupport(String bound) {
        assertThrows(PatternReadException.class,()->new PatternFilterInput(List.of(),bound,null,null,null).validated());
    }
    @ParameterizedTest @ValueSource(strings={"NaN","Infinity","1e3","","0x10","+1"})
    void rejectsInvalidWeight(String bound) {
        assertThrows(PatternReadException.class,()->new PatternFilterInput(List.of(),null,null,bound,null).validated());
    }
    @Test void rejectsReversedBoundsAndOutOfRangeItems() {
        for(var input:List.of(new PatternFilterInput(List.of(),"4","3",null,null),
                new PatternFilterInput(List.of(),null,null,"0.9","0.2"),
                new PatternFilterInput(List.of("0"),null,null,null,null),
                new PatternFilterInput(List.of("2147483648"),null,null,null,null),
                new PatternFilterInput(java.util.Collections.nCopies(101,"1"),null,null,null,null)))
            assertThrows(PatternReadException.class,input::validated);
    }
    @Test void literalUnicodeSearchDoesNotTreatWildcardAsRegexOrMatchAcrossFields() {
        assertEquals("chuot ban phim",DictionarySearch.normalize(" CHU\u1ed8T  B\u00e0n ph\u00edm "));
        assertEquals("dien",DictionarySearch.normalize("\u0110i\u1ec7n"));
        assertTrue(DictionarySearch.matches("chuot","Chu\u1ed9t", "SKU"));
        assertTrue(DictionarySearch.matches("sku-a","Name","SKU-A"));
        assertFalse(DictionarySearch.matches("%","Name","SKU"));
        assertTrue(DictionarySearch.matches("%","Name%","SKU"));
        assertFalse(DictionarySearch.matches("_","Name","SKU"));
        assertTrue(DictionarySearch.matches("","Name","SKU"));
        assertFalse(DictionarySearch.matches("a b","A","B"));
    }
    @Test void dictionaryServiceScopesRunAndNormalizesBeforeReader() {
        var reader=mock(DictionaryReader.class);var service=new DictionaryService(reader);
        service.search("A","SKU-A",null,null);service.search("B","SKU-A",0,20);
        verify(reader).search("A","sku-a",0,20);verify(reader).search("B","sku-a",0,20);
        assertThrows(PatternReadException.class,()->service.search("A","x".repeat(101),0,20));
        assertThrows(PatternReadException.class,()->service.search("A","",-1,20));
    }
    @Test void filteredServiceUsesOriginalAndMatchedTotalsSeparately() {
        var reader=mock(PatternListReader.class);var service=new PatternListService(reader);
        var input=new PatternFilterInput(List.of("2","1","2"),"2",null,null,null);
        var f=input.validated();
        when(reader.read("A",0,20,f)).thenReturn(new PatternListReader.Snapshot("SUCCEEDED",
                BigInteger.valueOf(5),BigInteger.valueOf(5),List.of(),BigInteger.ZERO,
                new PatternListResponse.WeightFilter(false,"POLICY_NOT_CONFIRMED","P")));
        var result=service.list("A",0,20,input);
        assertEquals("5",result.runPatternTotal());assertEquals("0",result.filteredTotal());
        assertFalse(result.weightFilter().enabled());
    }
    @Test void readinessBeforeWeightAndMismatchAgainstOriginalCount() {
        var reader=mock(PatternListReader.class);var service=new PatternListService(reader);
        var input=new PatternFilterInput(List.of(),null,null,"0",null);var f=input.validated();
        when(reader.read("A",0,20,f)).thenReturn(new PatternListReader.Snapshot("RUNNING",null,null,List.of()));
        assertEquals(PatternListResponse.ResultState.NOT_READY,service.list("A",0,20,input).resultState());
        when(reader.read("A",0,20,f)).thenReturn(new PatternListReader.Snapshot("SUCCEEDED",
                BigInteger.ONE,BigInteger.TWO,List.of(),BigInteger.ZERO,null));
        assertEquals(PatternReadException.Code.RESULT_INCONSISTENT,assertThrows(PatternReadException.class,
                ()->service.list("A",0,20,input)).getCode());
    }
}
