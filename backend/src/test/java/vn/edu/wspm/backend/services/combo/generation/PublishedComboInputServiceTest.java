package vn.edu.wspm.backend.services.combo.generation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.backend.dto.run.result.RunResultResponse.*;
import vn.edu.wspm.backend.services.combo.PublishedMiningResultProvider;
import vn.edu.wspm.backend.services.combo.evaluation.CandidateEvaluation;

class PublishedComboInputServiceTest {
    final PublishedMiningResultProvider producer = mock(PublishedMiningResultProvider.class);
    final PublishedComboInputService service = new PublishedComboInputService(producer);
    final String run = "10000000-0000-4000-8000-000000000001";
    PublishedBundle fixture() {
        var provenance = new Provenance("RUN_PROVENANCE_V1", "CONVERSION_ARTIFACT_V1", run, run,
                "a".repeat(64), "b".repeat(64), "c".repeat(64), "2");
        var meta = new Metadata(run,"SUCCEEDED","COMMITTED","V1","d".repeat(64),Instant.EPOCH,
                provenance,"2","4","2","COMPLETE_VERIFIED",false,true,true);
        var a = new Item(1,12,"product-A","Historical A","OLD-A",.5,"2","2");
        var b = new Item(2,27,"product-B","Historical B","OLD-B",.5,"2","2");
        var bundle = new PatternSummary("18446744073709551615",run,"<(12 27)>","e".repeat(64),
                "1",.5,.5,.5,"PRODUCER_POLICY",1,2);
        var sequential = new PatternSummary("2",run,"<(12)(27)>","f".repeat(64),
                "1",.5,.5,.5,"PRODUCER_POLICY",2,2);
        return new PublishedBundle(meta,List.of(new ExportPattern(bundle,List.of(new Itemset(1,List.of(a,b)))),
                new ExportPattern(sequential,List.of(new Itemset(1,List.of(a)),new Itemset(2,List.of(
                        new Item(1,27,b.productId(),b.productNameSnapshot(),b.skuSnapshot(),.5,"2","2")))))),
                List.of(new RunItem(12,a.productId(),a.productNameSnapshot(),a.skuSnapshot(),.5,"2","2"),
                        new RunItem(27,b.productId(),b.productNameSnapshot(),b.skuSnapshot(),.5,"2","2")));
    }
    @Test void preservesUnsignedIdentityNonDenseItemsAndTemporalStructure() {
        when(producer.requireEligible(run)).thenReturn(fixture());
        var p = service.prepare(run);
        assertEquals("18446744073709551615",p.patterns().getFirst().patternId());
        assertEquals("<(12 27)>",p.patterns().getFirst().canonicalPattern());
        assertEquals(2,p.patterns().get(1).itemsets().size());
        assertEquals(27,p.patterns().get(1).itemsets().get(1).items().getFirst().itemId());
        assertEquals("Historical A",p.dictionary().getFirst().nameSnapshot());
        assertEquals("1",p.patterns().getFirst().supportAbs());
        assertThrows(UnsupportedOperationException.class,()->p.patterns().clear());
        verify(producer).requireEligible(run);
    }
    @Test void integrityDoesNotGrantBusinessApproval() {
        when(producer.requireEligible(run)).thenReturn(fixture());
        var p = service.prepare(run);
        var result = service.evaluate(p,new PublishedComboInputService.BusinessContext(
                CandidateEvaluation.Purpose.UNCONFIRMED,null,null,Map.of(),null,Instant.EPOCH));
        assertFalse(result.completed());
        assertEquals("BLOCKED",service.summary(run).generationStatus());
        assertFalse(service.summary(run).blockers().isEmpty());
    }
    @Test void explicitControlledBusinessContextReusesEvaluatorAndGrouping() {
        when(producer.requireEligible(run)).thenReturn(fixture());
        var prepared = service.prepare(run);
        var policy = new CandidateEvaluation.Policy("FIXTURE_POLICY", "fixture-only-approved",
                Set.of("laptops"), Set.of("accessories"), CandidateEvaluation.EligibilityMode.CATALOG_ONLY);
        var catalog = Map.of("product-A", product("product-A","laptops"),
                "product-B", product("product-B","accessories"));
        var evaluated = service.evaluate(prepared,new PublishedComboInputService.BusinessContext(
                CandidateEvaluation.Purpose.DEMO_CATALOG,"fixture-purpose",policy,catalog,"fixture-catalog",Instant.EPOCH));
        assertTrue(evaluated.completed(), evaluated.sessionErrors().toString());
        assertEquals(2,evaluated.accepted());
        var context = vn.edu.wspm.backend.services.combo.grouping.CandidateGrouping.Context.from(
                evaluated.patterns().getFirst().candidate());
        var grouped = new vn.edu.wspm.backend.services.combo.grouping.CandidateGrouper().group(evaluated,context,
                new vn.edu.wspm.backend.services.combo.grouping.CandidateGrouping.WeightPolicyConfirmation("PRODUCER_POLICY","fixture-weight-only"));
        assertTrue(grouped.completed(),grouped.errors().toString());
        assertEquals(Set.of("SAME_ORDER_BUNDLE","SEQUENTIAL_CROSS_SELL"),
                grouped.groups().stream().map(g->g.comboKind().name()).collect(java.util.stream.Collectors.toSet()));
    }
    private CandidateEvaluation.CatalogProduct product(String id,String category) {
        return new CandidateEvaluation.CatalogProduct(id,category,"ACTIVE","ACTIVE",java.math.BigDecimal.ONE,
                "VND","1",CandidateEvaluation.Inventory.UNKNOWN,null);
    }
    @Test void producerFailureNeverBecomesEmptySuccessOrFallback() {
        when(producer.requireEligible(run)).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class,()->service.prepare(run));
        verifyNoMoreInteractionsAfterCall();
    }
    @Test void outerTransactionCannotWeakenProducerReadIsolation() {
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(vn.edu.wspm.backend.exception.ApplicationException.class,()->service.prepare(run));
            verifyNoInteractions(producer);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
    private void verifyNoMoreInteractionsAfterCall() { verify(producer).requireEligible(run); verifyNoMoreInteractions(producer); }
}
