package vn.edu.wspm.backend.services.combo.admin;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import vn.edu.wspm.backend.dto.combo.ComboAdminRead.*;
import vn.edu.wspm.backend.repositories.ComboAdminReader;
import vn.edu.wspm.backend.repositories.ComboAdminReader.*;
import static org.junit.jupiter.api.Assertions.*;

class ComboAdminServiceTest {
    private static final Metrics METRICS=new Metrics("9007199254740993",.5,.4);
    private static Row row(String status,Audit audit) {
        return new Row("C","Name","SEQUENTIAL_CROSS_SELL",status,"B","DRAFT","R","SUCCEEDED",
                "2026-11-05T00:00:00.000001Z",audit,METRICS,"Explanation","VND",BigDecimal.TEN,BigDecimal.TEN,"RANK","WEIGHT","1");
    }
    private static Source source(String id,boolean primary,String run,Metrics metrics) {
        return new Source(id,primary,run,"<(1)(2)>",metrics,4.0,"WEIGHT");
    }
    private static Item item(long set,long order,String id) {
        return new Item(set,order,id,"1",BigDecimal.ONE,set==1,false,"Snapshot-"+id,"SKU-"+id);
    }
    private static Bundle good() { return new Bundle(row("DRAFT",new Audit(null,null,null,null)),
            List.of(item(2,1,"M"),item(1,1,"L")),List.of(source("18446744073709551615",true,"R",METRICS),source("2",false,"R",METRICS))); }
    private static class Fake implements ComboAdminReader {
        Snapshot snapshot=new Snapshot(true,"5","1",List.of(good()));Bundle detail=good();Filters filters;long offset;int size;int calls;
        public Snapshot list(Filters filters,long offset,int size) { calls++;this.filters=filters;this.offset=offset;this.size=size;return snapshot; }
        public Bundle detail(String id) { calls++;return detail; }
    }
    @Test void threeFiltersTotalsAndLongOffsetArePreserved() {
        var f=new Fake();var result=new ComboAdminService(f).list("B","DRAFT","SEQUENTIAL_CROSS_SELL",Integer.MAX_VALUE,100);
        assertEquals(new Filters("B","DRAFT","SEQUENTIAL_CROSS_SELL"),f.filters);
        assertEquals(214748364700L,f.offset);assertEquals("5",result.scopeTotal());assertEquals("1",result.filteredTotal());
        assertEquals("DRAFT",result.items().getFirst().data().batchStatus());assertEquals("DRAFT",result.items().getFirst().data().comboStatus());
        assertEquals("18446744073709551615",result.items().getFirst().assessment().primaryPatternId());
    }
    @Test void absentBatchEmptyBatchFilteredEmptyAndOutOfRangeRemainDistinct() {
        var f=new Fake();var s=new ComboAdminService(f);
        f.snapshot=new Snapshot(false,"0","0",List.of());assertEquals(ComboReadException.Code.BATCH_NOT_FOUND,assertThrows(ComboReadException.class,()->s.list("B",null,null,0,20)).code());
        for(var counts:List.of(List.of("0","0"),List.of("4","0"),List.of("4","3"))) {
            f.snapshot=new Snapshot(true,counts.get(0),counts.get(1),List.of());var p=s.list("B",null,null,99,20);
            assertEquals(counts.get(0),p.scopeTotal());assertEquals(counts.get(1),p.filteredTotal());assertTrue(p.items().isEmpty());
        }
    }
    @Test void validationOccursBeforeReaderAndDefaultsAreExplicit() {
        var f=new Fake();var s=new ComboAdminService(f);
        assertThrows(ComboReadException.class,()->s.list("",null,null,0,20));
        assertThrows(ComboReadException.class,()->s.list(null,"PENDING",null,0,20));
        assertThrows(ComboReadException.class,()->s.list(null,null,"BUNDLE",0,20));
        assertThrows(ComboReadException.class,()->s.list(null,null,null,-1,20));
        assertThrows(ComboReadException.class,()->s.list(null,null,null,0,101));
        assertThrows(ComboReadException.class,()->s.detail(" "));assertEquals(0,f.calls);
        var page=s.list(null,null,null,null,null);assertEquals(0,page.page());assertEquals(20,page.size());
    }
    @Test void detailKeepsBoundariesPositionsFlagsAndExactSources() {
        var d=new ComboAdminService(new Fake()).detail("C");assertEquals(List.of(1L,2L),d.itemsets().stream().map(Itemset::itemsetOrder).toList());
        assertEquals("L",d.itemsets().getFirst().items().getFirst().productId());assertFalse(d.itemsets().get(1).items().getFirst().anchorEligible());
        assertEquals(2,d.sources().size());assertEquals("9007199254740993",d.sources().getFirst().metrics().supportAbs());
        assertTrue(d.assessment().allowedActions().isEmpty());assertTrue(codes(d.assessment()).contains("ACTION_ELIGIBILITY_UNVERIFIED"));
    }
    @Test void missingMultipleForeignSourcesAndMetricMismatchProduceIssuesWithoutRepair() {
        var base=good();var f=new Fake();var s=new ComboAdminService(f);
        for(var sources:List.of(List.<Source>of(),List.of(source("1",false,"R",METRICS)),
                List.of(source("1",true,"R",METRICS),source("2",true,"R",METRICS)))) {
            f.detail=new Bundle(base.row(),base.items(),sources);assertTrue(codes(s.detail("C").assessment()).contains("PRIMARY_SOURCE_INVALID"));
        }
        var mismatch=new Metrics("2",.2,.9);f.detail=new Bundle(base.row(),base.items(),List.of(source("1",true,"OTHER",mismatch)));
        var d=s.detail("C");assertTrue(codes(d.assessment()).containsAll(List.of("SOURCE_RUN_MISMATCH","PRIMARY_METRICS_MISMATCH")));
        assertEquals(mismatch,d.sources().getFirst().metrics());assertEquals(METRICS,d.data().metrics());
    }
    @Test void reviewerIdSurvivesMissingDisplayNameAndMissingAuditIsReported() {
        var f=new Fake();var base=good();var s=new ComboAdminService(f);
        var audit=new Audit("admin-id",null,"2026-11-05T00:00:00.000000Z",null);
        f.detail=new Bundle(row("APPROVED",audit),base.items(),base.sources());var d=s.detail("C");
        assertEquals("admin-id",d.data().review().reviewedBy());assertNull(d.data().review().reviewerDisplayName());
        assertFalse(codes(d.assessment()).contains("REVIEW_AUDIT_INCONSISTENT"));assertTrue(d.assessment().allowedActions().isEmpty());
        f.detail=new Bundle(row("REJECTED",new Audit(null,null,null,null)),base.items(),base.sources());
        assertTrue(codes(s.detail("C").assessment()).contains("REVIEW_AUDIT_INCONSISTENT"));
    }
    @Test void unknownStatusAndMissingDictionaryAreNotRewritten() {
        var f=new Fake();var base=good();f.detail=new Bundle(row("PENDING",new Audit(null,null,null,null)),
                List.of(new Item(1,1,"L","1",BigDecimal.ONE,true,false,null,null)),base.sources());
        var d=new ComboAdminService(f).detail("C");assertEquals("PENDING",d.data().comboStatus());
        assertTrue(codes(d.assessment()).containsAll(List.of("UNKNOWN_COMBO_STATUS","DICTIONARY_SNAPSHOT_MISSING")));
        assertTrue(d.assessment().allowedActions().isEmpty());
    }
    @Test void bundleMembersStayInOneItemsetAndDraftAuditNeverEnablesReview() {
        var f=new Fake();var base=good();
        f.detail=new Bundle(row("DRAFT",new Audit("old-admin",null,"2026-01-01T00:00:00.000000Z","old note")),
                List.of(item(1,2,"M"),item(1,1,"L")),base.sources());
        var d=new ComboAdminService(f).detail("C");assertEquals(1,d.itemsets().size());
        assertEquals(List.of("L","M"),d.itemsets().getFirst().items().stream().map(Item::productId).toList());
        assertTrue(codes(d.assessment()).contains("REVIEW_AUDIT_INCONSISTENT"));assertTrue(d.assessment().allowedActions().isEmpty());
        assertEquals("old-admin",d.data().review().reviewedBy());
    }
    @Test void missingComboAndDatabaseFailuresAreNotEmptySuccess() {
        var f=new Fake();f.detail=null;assertEquals(ComboReadException.Code.COMBO_NOT_FOUND,assertThrows(ComboReadException.class,()->new ComboAdminService(f).detail("missing")).code());
        var broken=new ComboAdminReader() {
            public Snapshot list(Filters f,long o,int s) { throw new DataAccessResourceFailureException("test"); }
            public Bundle detail(String id) { throw new DataAccessResourceFailureException("test"); }
        };
        var s=new ComboAdminService(broken);assertThrows(DataAccessResourceFailureException.class,()->s.list(null,null,null,0,20));
        assertThrows(DataAccessResourceFailureException.class,()->s.detail("C"));
    }
    private static List<String> codes(Assessment a) { return a.blockingIssues().stream().map(Issue::code).toList(); }
}
