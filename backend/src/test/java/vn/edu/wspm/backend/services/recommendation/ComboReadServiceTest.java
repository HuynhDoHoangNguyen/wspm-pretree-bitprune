package vn.edu.wspm.backend.services.recommendation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.mappers.ComboReadMapper;
import vn.edu.wspm.backend.repositories.recommendation.ComboReadRepository;
import vn.edu.wspm.backend.repositories.recommendation.ComboReadRepository.*;

class ComboReadServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
    private final ComboReadRepository repository = mock(ComboReadRepository.class);
    private final ComboReadService service = new ComboReadService(repository, new ComboReadMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final BatchRow batch = new BatchRow("b", "r", "s", "WSPM_PRETREE",
            ComboReadService.RANKING_POLICY, "stored-policy", 10, BigInteger.valueOf(100));

    static ComboRow combo(String id, String status, String kind, Instant from, Instant to) {
        return new ComboRow(id, "Combo", kind, status, "Explanation", from, to, NOW.minusSeconds(10),
                "7", "<(1 2)>", BigInteger.valueOf(12), .12, .45, "VND",
                new BigDecimal("200.00"), new BigDecimal("150.00"), "FIXED_PRICE",
                new BigDecimal("150.0000"), null);
    }
    static ItemRow item(String comboId) {
        return new ItemRow(comboId, 1, 1, 2, true, true, new BigDecimal("100.00"),
                "p", "Product", "SKU", "cat", "/uploads/products/p.jpg", new BigDecimal("999.00"));
    }
    private void active() { when(repository.activeBatches()).thenReturn(List.of(batch)); }

    @Test void personalProductContextFiltersBeforeCapNotAfterGlobalTopK() {
        when(repository.activeBatches()).thenReturn(List.of(new BatchRow("b","r","s","WSPM_PRETREE",
                ComboReadService.RANKING_POLICY,"stored-policy",1,BigInteger.TEN)));
        when(repository.productExists("target")).thenReturn(true);
        when(repository.history("user")).thenReturn(List.of(new HistoryRow("order",NOW,"p")));
        when(repository.candidates("b",NOW,0,false)).thenReturn(List.of(
                combo("first","PUBLISHED","SEQUENTIAL_CROSS_SELL",null,null),
                combo("second","PUBLISHED","SEQUENTIAL_CROSS_SELL",null,null)));
        when(repository.items(List.of("first","second"))).thenReturn(List.of(item("first"),item("second"),
                new ItemRow("first",2,1,1,false,true,null,"other","Other",null,null,null,BigDecimal.ONE),
                new ItemRow("second",2,1,1,false,true,null,"target","Target",null,null,null,BigDecimal.ONE)));
        var result=service.personalized("user","target");
        assertTrue(result.items().isEmpty()); // target is itself the next-step suggestion
        when(repository.productExists("p")).thenReturn(true);
        assertEquals(1, service.personalized("user", "p").items().size());
    }

    @Test void selfSuggestionIsRemovedBeforeCapSoAnotherValidComboCanFillIt() {
        when(repository.activeBatches()).thenReturn(List.of(new BatchRow("b","r","s","WSPM_PRETREE",
                ComboReadService.RANKING_POLICY,"stored-policy",1,BigInteger.TEN)));
        when(repository.productExists("p")).thenReturn(true);
        when(repository.candidates("b",NOW,0,false)).thenReturn(List.of(
                combo("self","PUBLISHED","SEQUENTIAL_CROSS_SELL",null,null),
                combo("valid","PUBLISHED","SEQUENTIAL_CROSS_SELL",null,null)));
        when(repository.history("user")).thenReturn(List.of(new HistoryRow("order",NOW,"past")));
        when(repository.items(List.of("self","valid"))).thenReturn(List.of(
                new ItemRow("self",1,1,1,false,true,null,"past","Past",null,null,null,BigDecimal.ONE),
                new ItemRow("self",2,1,1,false,true,null,"p","Current",null,null,null,BigDecimal.ONE),
                new ItemRow("valid",1,1,1,false,true,null,"past","Past",null,null,null,BigDecimal.ONE),
                new ItemRow("valid",2,1,1,false,true,null,"future","Future",null,null,null,BigDecimal.ONE),
                new ItemRow("valid",3,1,1,false,true,null,"p","Current",null,null,null,BigDecimal.ONE)));
        var result = service.personalized("user", "p");
        assertEquals(List.of("valid"), result.items().stream().map(r -> r.combo().comboId()).toList());
        assertEquals(List.of("future"), result.items().getFirst().suggestedProductIds());
        assertEquals(1, result.items().getFirst().rank());
    }

    @Test void personalRankingAppliesCapAfterMatchingAcrossCandidatePages() {
        active();
        when(repository.history("user")).thenReturn(List.of(new HistoryRow("order",NOW,"p")));
        var nonMatches=java.util.stream.IntStream.range(0,20)
                .mapToObj(i -> combo("same"+i,"PUBLISHED","SAME_ORDER_BUNDLE",null,null)).toList();
        when(repository.candidates("b",NOW,0,false)).thenReturn(nonMatches);
        var temporal=combo("next","PUBLISHED","SEQUENTIAL_CROSS_SELL",null,null);
        when(repository.candidates("b",NOW,20,false)).thenReturn(List.of(temporal));
        when(repository.items(List.of("next"))).thenReturn(List.of(item("next"),
                new ItemRow("next",2,1,1,false,true,BigDecimal.ONE,"future","Next",null,null,null,BigDecimal.ONE)));
        var result=service.personalized("user");
        assertEquals(1,result.items().size());assertEquals(List.of("future"),result.items().getFirst().suggestedProductIds());
        assertEquals(1,result.items().getFirst().rank());
        verify(repository).candidates("b",NOW,20,false);
    }

    @Test void emptyDatabaseReturnsEmptyWithoutFixture() {
        when(repository.productExists("p")).thenReturn(true);
        when(repository.activeBatches()).thenReturn(List.of());
        var response = service.topK("p", null);
        assertEquals(5, response.requestedK());
        assertEquals(0, response.effectiveK());
        assertTrue(response.items().isEmpty());
        assertNull(response.provenance());
        verify(repository, never()).topK(any(), any(), anyInt(), any());
    }

    @ParameterizedTest @ValueSource(ints = {0, -1, 21, Integer.MAX_VALUE})
    void rejectsInvalidKBeforeReading(int k) {
        assertEquals(ErrorCode.VALIDATION_FAILED,
                assertThrows(ApplicationException.class, () -> service.topK("p", k)).errorCode());
        verifyNoInteractions(repository);
    }

    @Test void missingProductIs404EvenWithoutBatch() {
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND,
                assertThrows(ApplicationException.class, () -> service.topK("missing", 4)).errorCode());
        verify(repository, never()).activeBatches();
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "bad\u0000id"})
    void rejectsInvalidIdentifiers(String id) {
        assertThrows(ApplicationException.class, () -> service.detail(id));
        verifyNoInteractions(repository);
    }

    @Test void usesBatchCapAndPreservesRankedRepositoryOrder() {
        active(); when(repository.productExists("p")).thenReturn(true);
        var z = combo("z", "PUBLISHED", "SAME_ORDER_BUNDLE", NOW, NOW.plusSeconds(1));
        var a = combo("a", "PUBLISHED", "SAME_ORDER_BUNDLE", null, null);
        when(repository.topK("b", "p", 10, NOW)).thenReturn(List.of(z, a));
        when(repository.items(List.of("z", "a"))).thenReturn(List.of(item("z"), item("a")));
        var response = service.topK("p", 20);
        assertEquals(10, response.effectiveK());
        assertEquals(List.of("z", "a"), response.items().stream().map(r -> r.combo().comboId()).toList());
        assertEquals(List.of(1, 2), response.items().stream().map(r -> r.rank()).toList());
        verify(repository, times(1)).items(List.of("z", "a"));
    }

    @ParameterizedTest @ValueSource(ints={2,5})
    void omittedKUsesBatchCapForPublicAndPersonalReads(int k){
        when(repository.activeBatches()).thenReturn(List.of(new BatchRow("b","r","s","WSPM_PRETREE",ComboReadService.RANKING_POLICY,"stored-policy",k,BigInteger.TEN)));
        when(repository.productExists("p")).thenReturn(true);
        var bundles=java.util.stream.IntStream.range(0,k).mapToObj(i->combo("bundle"+i,"PUBLISHED","SAME_ORDER_BUNDLE",null,null)).toList();
        when(repository.topK("b","p",k,NOW)).thenReturn(bundles);
        when(repository.items(bundles.stream().map(ComboRow::comboId).toList())).thenReturn(bundles.stream().map(c->item(c.comboId())).toList());
        var result=service.topK("p",null);
        assertEquals(k,result.requestedK());assertEquals(k,result.effectiveK());assertEquals(k,result.items().size());
        when(repository.history("user")).thenReturn(List.of(new HistoryRow("order",NOW,"p")));
        var temporal=java.util.stream.IntStream.range(0,8).mapToObj(i->combo("next"+i,"PUBLISHED","SEQUENTIAL_CROSS_SELL",null,null)).toList();
        when(repository.candidates("b",NOW,0,false)).thenReturn(temporal);
        var contents=new java.util.ArrayList<ItemRow>();
        for(var c:temporal){contents.add(item(c.comboId()));contents.add(new ItemRow(c.comboId(),2,1,1,false,true,null,"future","Future",null,null,null,BigDecimal.ONE));}
        when(repository.items(temporal.stream().map(ComboRow::comboId).toList())).thenReturn(contents);
        assertEquals(k,service.personalized("user").items().size());
        service.topK("p",1);verify(repository).topK("b","p",1,NOW);
    }
    @Test void unknownComboAndMissingBatchAre404() {
        when(repository.activeBatches()).thenReturn(List.of());
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND,
                assertThrows(ApplicationException.class, () -> service.detail("missing")).errorCode());
        active(); when(repository.detail("b", "missing", NOW)).thenReturn(List.of());
        assertThrows(ApplicationException.class, () -> service.detail("missing"));
    }

    @Test void unsignedBatchCapDoesNotOverflowAndAnEmptySelectionDoesNotReadItems() {
        when(repository.productExists("p")).thenReturn(true);
        when(repository.activeBatches()).thenReturn(List.of(new BatchRow("b", "r", "s", "WSPM_PRETREE",
                ComboReadService.RANKING_POLICY, "stored-policy", 4294967295L, BigInteger.TEN)));
        when(repository.topK("b", "p", 20, NOW)).thenReturn(List.of());
        assertEquals(20, service.topK("p", 20).effectiveK());
        verify(repository, never()).items(any());
    }

    @Test void multipleActiveBatchesFailClosedForBothReads() {
        when(repository.productExists("p")).thenReturn(true);
        when(repository.activeBatches()).thenReturn(List.of(batch, batch));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE,
                assertThrows(ApplicationException.class, () -> service.topK("p", 4)).errorCode());
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE,
                assertThrows(ApplicationException.class, () -> service.detail("c")).errorCode());
        verify(repository, never()).items(any());
    }

    @Test void unsupportedRankingPolicyIsUnavailable() {
        when(repository.activeBatches()).thenReturn(List.of(new BatchRow("b", "r", "s", "WSPM_PRETREE",
                "COMPOSITE_SCORE", "policy", 4, BigInteger.TEN)));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE,
                assertThrows(ApplicationException.class, () -> service.detail("c")).errorCode());
    }

    @ParameterizedTest @ValueSource(strings = {"DRAFT", "APPROVED", "REJECTED", "ARCHIVED", "published"})
    void neverExposesNonpublicDetail(String status) {
        active(); when(repository.detail("b", "c", NOW)).thenReturn(List.of(combo("c", status, "SAME_ORDER_BUNDLE", null, null)));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND,
                assertThrows(ApplicationException.class, () -> service.detail("c")).errorCode());
        verify(repository, never()).items(any());
    }

    @ParameterizedTest @ValueSource(strings = {"SEQUENTIAL_CROSS_SELL", "MIXED_SEQUENCE"})
    void temporalKindsAreExcludedFromPublicReads(String kind) {
        active(); when(repository.productExists("p")).thenReturn(true);
        var row = combo("c", "PUBLISHED", kind, null, null);
        when(repository.detail("b", "c", NOW)).thenReturn(List.of(row));
        assertThrows(ApplicationException.class, () -> service.detail("c"));
        when(repository.topK("b", "p", 4, NOW)).thenReturn(List.of(row));
        assertTrue(service.topK("p",4).items().isEmpty());
    }

    @Test void personalPopupVerifiesExactComboEvenOutsideTopKAndRejectsAnotherHistory() {
        active();
        when(repository.detail("b","c",NOW)).thenReturn(List.of(combo("c","PUBLISHED","MIXED_SEQUENCE",null,null)));
        when(repository.items(List.of("c"))).thenReturn(List.of(item("c"),
                new ItemRow("c",2,1,1,false,true,null,"next1","Next 1",null,null,null,BigDecimal.ONE),
                new ItemRow("c",2,2,1,false,true,null,"next2","Next 2",null,null,null,BigDecimal.ONE)));
        when(repository.history("owner")).thenReturn(List.of(new HistoryRow("o1",NOW,"p")));
        var result = service.personalizedCombo("owner","c");
        assertEquals(List.of("next1","next2"),result.items().getFirst().suggestedProductIds());
        assertEquals(2,result.items().getFirst().nextItemsetOrder());
        assertThrows(ApplicationException.class, () -> service.personalizedCombo("other","c"));
        verify(repository,never()).candidates(any(),any(),anyInt(),anyBoolean());
    }

    @Test void latestReturnsFiveBundlesAfterKindFilterAndRetainsRepositoryOrder() {
        active();
        when(repository.candidates("b",NOW,0,true)).thenReturn(List.of(
                combo("temporal","PUBLISHED","SEQUENTIAL_CROSS_SELL",null,null),
                combo("new","PUBLISHED","SAME_ORDER_BUNDLE",null,null),
                combo("older","PUBLISHED","SAME_ORDER_BUNDLE",null,null),
                combo("third","PUBLISHED","SAME_ORDER_BUNDLE",null,null),
                combo("fourth","PUBLISHED","SAME_ORDER_BUNDLE",null,null),
                combo("fifth","PUBLISHED","SAME_ORDER_BUNDLE",null,null),
                combo("sixth","PUBLISHED","SAME_ORDER_BUNDLE",null,null)));
        when(repository.items(List.of("new","older","third","fourth","fifth"))).thenReturn(List.of(item("new"),item("older"),item("third"),item("fourth"),item("fifth")));
        assertEquals(List.of("new","older","third","fourth","fifth"),service.latest().stream().map(c -> c.comboId()).toList());
    }

    @Test void utcValidityIncludesFromButExcludesToAndFuture() {
        active(); when(repository.items(List.of("c"))).thenReturn(List.of(item("c")));
        when(repository.detail("b", "c", NOW)).thenReturn(List.of(combo("c", "PUBLISHED", "SAME_ORDER_BUNDLE", NOW, NOW.plusSeconds(1))));
        assertEquals(NOW, service.detail("c").evaluatedAt());
        when(repository.detail("b", "c", NOW)).thenReturn(List.of(combo("c", "PUBLISHED", "SAME_ORDER_BUNDLE", null, NOW)));
        assertThrows(ApplicationException.class, () -> service.detail("c"));
        when(repository.detail("b", "c", NOW)).thenReturn(List.of(combo("c", "PUBLISHED", "SAME_ORDER_BUNDLE", NOW.plusSeconds(1), null)));
        assertThrows(ApplicationException.class, () -> service.detail("c"));
    }

    @Test void preservesPublishPriceNotCurrentPriceAndPrimaryMetrics() {
        active(); when(repository.detail("b", "c", NOW)).thenReturn(List.of(combo("c", "PUBLISHED", "SAME_ORDER_BUNDLE", null, null)));
        when(repository.items(List.of("c"))).thenReturn(List.of(item("c")));
        var result = service.detail("c");
        assertEquals("150.00", result.pricing().referenceSalePrice());
        assertEquals("150.0000", result.pricing().discountValue());
        assertEquals("100.00", result.items().getFirst().unitPriceSnapshot());
        assertEquals("999.00", result.items().getFirst().product().currentPrice());
        assertEquals(2, result.items().getFirst().quantity());
        assertFalse(result.pricing().isCheckoutQuote());
        assertEquals(BigInteger.valueOf(12), result.metrics().supportAbs());
        assertEquals("7", result.provenance().primaryPatternId());
    }

    @Test void storageFailureUsesSharedSafeError() {
        when(repository.productExists("p")).thenThrow(new DataAccessResourceFailureException("private SQL"));
        var error = assertThrows(ApplicationException.class, () -> service.topK("p", 4));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, error.errorCode());
        assertFalse(error.getMessage().contains("SQL"));
        assertNull(error.getCause());
    }
}
