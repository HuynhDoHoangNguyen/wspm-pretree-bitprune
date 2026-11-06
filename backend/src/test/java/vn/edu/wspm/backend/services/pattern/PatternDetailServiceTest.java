package vn.edu.wspm.backend.services.pattern;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.wspm.backend.repositories.PatternDetailReader;
import vn.edu.wspm.backend.repositories.PatternDetailReader.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatternDetailServiceTest {
    private final PatternDetailReader reader=mock(PatternDetailReader.class);
    private final PatternDetailService service=new PatternDetailService(reader);
    private Header header(String run,String id,String canonical,int count,int sets) {
        return new Header(id,run,canonical,"9007199254740993",.25,1.5,.7,"POLICY",count,sets);
    }
    private Row row(long set,long order,long item,String name,boolean mapped) {
        return new Row(set,order,item,mapped,"p"+name,name,"SKU-"+name,null);
    }
    private Snapshot ready(Header h,List<Row> rows) {
        return new Snapshot("SUCCEEDED",BigInteger.ONE,BigInteger.ONE,"9007199254740995",h,rows);
    }
    @Test void bundleIsOneGroupAndNullItemWeightIsMapped() {
        when(reader.read("A","1")).thenReturn(ready(header("A","1","<(1 2)>",2,1),
                List.of(row(1,2,2,"B",true),row(1,1,1,"A",true))));
        var r=service.detail("A","1");
        assertEquals(1,r.itemsets().size()); assertEquals(2,r.itemsets().getFirst().items().size());
        assertEquals("A",r.itemsets().getFirst().items().getFirst().nameSnapshot());
        assertEquals("MAPPED",r.itemsets().getFirst().items().getFirst().mappingStatus().name());
        assertNull(r.itemsets().getFirst().items().getFirst().itemWeight());
        assertEquals("COMPLETE",r.integrityStatus().name());
    }
    @Test void sequentialGroupsAndDictionaryScopeStaySeparate() {
        for(String run:List.of("A","B")) when(reader.read(run,"1")).thenReturn(ready(
                header(run,"1","<(1)(2)>",2,2),List.of(row(2,1,2,run+"2",true),row(1,1,1,run+"1",true))));
        var a=service.detail("A","1"); var b=service.detail("B","1");
        assertEquals(2,a.itemsets().size());
        assertEquals("A1",a.itemsets().getFirst().items().getFirst().nameSnapshot());
        assertEquals("B1",b.itemsets().getFirst().items().getFirst().nameSnapshot());
        verify(reader).read("A","1"); verify(reader).read("B","1");
    }
    @Test void missingMappingKeepsOriginalPositionsAndNullsAllMappingFields() {
        when(reader.read("A","1")).thenReturn(ready(header("A","1","<(1)(2)>",2,2),
                List.of(row(1,1,1,"A",true),row(2,1,2,"wrong-fallback",false))));
        var r=service.detail("A","1"); var item=r.itemsets().get(1).items().getFirst();
        assertEquals(2,r.itemsets().get(1).itemsetOrder()); assertEquals(2,item.itemId());
        assertEquals(1,item.itemOrder()); assertEquals("MISSING",item.mappingStatus().name());
        assertNull(item.productId()); assertNull(item.nameSnapshot()); assertNull(item.skuSnapshot());
        assertEquals("INCOMPLETE",r.integrityStatus().name());
        assertEquals("MISSING_RUN_DICTIONARY_ITEM",r.issues().getFirst().code());
    }
    @ParameterizedTest @ValueSource(strings={"QUEUED","RUNNING","FAILED","LEGACY_UNVERIFIED"})
    void unavailableWinsBeforePatternLookup(String status) {
        when(reader.read("A","1")).thenReturn(new Snapshot(status,null,null,null,null,List.of()));
        error(PatternReadException.Code.RESULTS_NOT_AVAILABLE,()->service.detail("A","1"));
    }
    @Test void notFoundAndWrongRunHaveSameCode() {
        when(reader.read("A","1")).thenReturn(ready(null,List.of()));
        error(PatternReadException.Code.PATTERN_NOT_FOUND,()->service.detail("A","1"));
        when(reader.read("A","1")).thenReturn(ready(header("B","1","<(1)>",1,1),List.of()));
        error(PatternReadException.Code.PATTERN_NOT_FOUND,()->service.detail("A","1"));
    }
    @Test void missingRunAndInconsistentCountHaveDistinctCodes() {
        error(PatternReadException.Code.RUN_NOT_FOUND,()->service.detail("A","1"));
        when(reader.read("A","1")).thenReturn(new Snapshot("SUCCEEDED",null,BigInteger.ZERO,null,null,List.of()));
        error(PatternReadException.Code.RESULT_INCONSISTENT,()->service.detail("A","1"));
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","1.5","1e3","01","18446744073709551616","abc"})
    void invalidUnsignedIdRejectedBeforeQuery(String id) {
        error(PatternReadException.Code.INVALID_PATTERN_ID,()->service.detail("A",id));
        verifyNoInteractions(reader);
    }
    @Test void fullUnsignedBigintAndMetricsAreNotRounded() {
        String id="18446744073709551615";
        when(reader.read("A",id)).thenReturn(ready(header("A",id,"<(1)>",1,1),List.of(row(1,1,1,"A",true))));
        var r=service.detail("A",id);
        assertEquals(id,r.patternId()); assertEquals("9007199254740993",r.supportAbs());
        assertEquals("9007199254740995",r.sequenceCount()); assertEquals(1.5,r.weightedSupport());
        String json=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(r);
        assertTrue(json.contains("\"patternId\":\"18446744073709551615\""));
        assertTrue(json.contains("\"supportAbs\":\"9007199254740993\""));
        assertTrue(json.contains("\"sequenceCount\":\"9007199254740995\""));
    }
    @Test void canonicalCountAndPositionMismatchPreservesEvidence() {
        when(reader.read("A","1")).thenReturn(ready(header("A","1","<(9)>",3,1),List.of(row(2,4,1,"A",true))));
        var r=service.detail("A","1");
        assertEquals("<(9)>",r.canonicalPattern()); assertEquals(2,r.itemsets().getFirst().itemsetOrder());
        assertEquals(4,r.itemsets().getFirst().items().getFirst().itemOrder());
        assertTrue(r.issues().stream().anyMatch(i->i.code().equals("STRUCTURE_MISMATCH")));
    }
    private void error(PatternReadException.Code code,Runnable action) {
        assertEquals(code,assertThrows(PatternReadException.class,action::run).getCode());
    }
}
