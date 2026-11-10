package vn.edu.wspm.backend.controllers.api.v1.recommendation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.exception.GlobalExceptionHandler;
import vn.edu.wspm.backend.mappers.ComboReadMapper;
import vn.edu.wspm.backend.repositories.recommendation.ComboReadRepository;
import vn.edu.wspm.backend.repositories.recommendation.ComboReadRepository.*;
import vn.edu.wspm.backend.services.recommendation.ComboReadService;
import vn.edu.wspm.backend.web.common.RequestIdFilter;

/** Real controller/service/mapper with mocked read repository, never database fixtures. */
class ComboReadControllerTest {
    private static final String URL = "/api/v1/recommendations/combos";
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
    private final ComboReadRepository repository = mock(ComboReadRepository.class);
    private MockMvc mvc;
    private final BatchRow batch = new BatchRow("b", "r", "s", "WSPM_PRETREE",
            ComboReadService.RANKING_POLICY, "stored-policy", 4, BigInteger.valueOf(100));

    @BeforeEach void setup() {
        var service = new ComboReadService(repository, new ComboReadMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        mvc = MockMvcBuilders.standaloneSetup(new ComboReadController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter()).build();
    }
    private void active() { when(repository.activeBatches()).thenReturn(List.of(batch)); }
    private ComboRow combo(String status, String kind) {
        return new ComboRow("c", "Combo", kind, status, "explanation", null, null, NOW,
                "7", "<(1 2)>", BigInteger.TEN, .1, .5, "VND",
                new BigDecimal("200.00"), new BigDecimal("150.00"), "FIXED_PRICE",
                new BigDecimal("150.0000"), null);
    }

    @Test void defaultKEmpty200UsesCommonEnvelopeAndRequestId() throws Exception {
        when(repository.productExists("p")).thenReturn(true);
        when(repository.activeBatches()).thenReturn(List.of());
        var response = mvc.perform(get(URL).param("productId", "p")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Request completed successfully."))
                .andExpect(jsonPath("$.data.requestedK").value(4))
                .andExpect(jsonPath("$.data.effectiveK").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty()).andReturn().getResponse();
        assertEquals(response.getHeader("X-Request-Id"), JsonMapper.builder().build()
                .readTree(response.getContentAsString()).get("requestId").asString());
    }

    @Test void maxKAcceptedAndPassedToReadLayer() throws Exception {
        when(repository.productExists("p")).thenReturn(true);active();
        when(repository.topK("b", "p", 4, NOW)).thenReturn(List.of());
        mvc.perform(get(URL).param("productId", "p").param("k", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.requestedK").value(20))
                .andExpect(jsonPath("$.data.effectiveK").value(4));
        verify(repository).topK("b", "p", 4, NOW);
    }

    @ParameterizedTest @ValueSource(strings = {"", "0", "-1", "21", "1.5", "abc", "+4", " 4", "999999999999999999999"})
    void invalidKIsSafe400(String k) throws Exception {
        mvc.perform(get(URL).param("productId", "p").param("k", k))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("k"));
        verifyNoInteractions(repository);
    }

    @Test void missingBlankLongAndControlProductIdsAre400() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isBadRequest());
        for (String id : List.of(" ", "a".repeat(41), "bad\u0000id"))
            mvc.perform(get(URL).param("productId", id)).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test void unknownDuplicateAndRankingParametersAreRejectedBeforeLookup() throws Exception {
        for (String key : List.of("sort", "rankingPolicy", "policy", "limit", "unexpected"))
            mvc.perform(get(URL).param("productId", "p").param(key, "private-value"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details[0].field").value("query"));
        mvc.perform(get(URL).param("productId", "p", "q")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("productId", "p").param("k", "4", "4")).andExpect(status().isBadRequest());
        mvc.perform(get(URL + "/c").param("productId", "p")).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test void detailIdentifierValidationPrecedesLookup() throws Exception {
        mvc.perform(get(URL + "/" + "a".repeat(41))).andExpect(status().isBadRequest());
        mvc.perform(get(URL + "/ ")).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test void missingProductAndComboHaveShared404ErrorAndRequestId() throws Exception {
        var result = mvc.perform(get(URL).param("productId", "missing"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.path").value(URL))
                .andExpect(jsonPath("$.details").isArray()).andReturn().getResponse();
        assertEquals(result.getHeader("X-Request-Id"), JsonMapper.builder().build()
                .readTree(result.getContentAsString()).get("requestId").asString());
        when(repository.activeBatches()).thenReturn(List.of());
        mvc.perform(get(URL + "/missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource was not found."));
    }

    @ParameterizedTest @ValueSource(strings = {"DRAFT", "APPROVED", "REJECTED", "ARCHIVED"})
    void privateComboIsIndistinguishableFromMissing(String state) throws Exception {
        active();when(repository.detail("b", "c", NOW)).thenReturn(List.of(combo(state, "SAME_ORDER_BUNDLE")));
        mvc.perform(get(URL + "/c")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource was not found."));
        verify(repository, never()).items(any());
    }

    @ParameterizedTest @ValueSource(strings = {"SEQUENTIAL_CROSS_SELL", "MIXED_SEQUENCE"})
    void temporalRowsWithoutItemsRemainFailClosedThroughHttp(String kind) throws Exception {
        active();when(repository.productExists("p")).thenReturn(true);
        when(repository.topK("b", "p", 4, NOW)).thenReturn(List.of(combo("PUBLISHED", kind)));
        when(repository.detail("b", "c", NOW)).thenReturn(List.of(combo("PUBLISHED", kind)));
        mvc.perform(get(URL).param("productId", "p")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get(URL + "/c")).andExpect(status().isNotFound());
    }

    @Test void publishedDetailPreservesDecimalStringsAndProvenance() throws Exception {
        active();when(repository.detail("b", "c", NOW)).thenReturn(List.of(combo("PUBLISHED", "SAME_ORDER_BUNDLE")));
        when(repository.items(List.of("c"))).thenReturn(List.of(new ItemRow("c", 1, 1, 2,
                true, true, new BigDecimal("100.00"), "p", "Product", "SKU", "cat", "/uploads/products/p.jpg", new BigDecimal("999.00"))));
        mvc.perform(get(URL + "/c")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pricing.referenceSalePrice").value("150.00"))
                .andExpect(jsonPath("$.data.pricing.isCheckoutQuote").value(false))
                .andExpect(jsonPath("$.data.provenance.primaryPatternId").value("7"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2));
    }

    @Test void ambiguousBatchAndUnsupportedPolicyAreSafe503() throws Exception {
        when(repository.activeBatches()).thenReturn(List.of(batch, batch));
        mvc.perform(get(URL + "/c")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Service is temporarily unavailable."));
        when(repository.activeBatches()).thenReturn(List.of(new BatchRow("b", "r", "s", "WSPM_PRETREE", "private-policy", "stored", 4, BigInteger.TEN)));
        mvc.perform(get(URL + "/c")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Service is temporarily unavailable."));
    }
}
