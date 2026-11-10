package vn.edu.wspm.backend.controllers.api.v1.admin;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.dto.auth.IdentityResponse;
import vn.edu.wspm.backend.dto.run.result.RunResultResponse.*;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.services.run.query.RunResultQueryService;
import vn.edu.wspm.backend.web.auth.*;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.web.wdm.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Project A P3: real MVC/ADMIN filters with mocked identity/storage; no Boot or database. */
class PatternReaderControllerTest {
    static final String RUN = "10000000-0000-4000-8000-000000000001";
    static final String BASE = "/api/v1/admin/mining-runs/" + RUN;
    final RunResultQueryService service = mock(RunResultQueryService.class);
    final SharedRequestAuthenticator authenticator = mock(SharedRequestAuthenticator.class);
    MockMvc mvc;

    @BeforeEach void setup() {
        when(authenticator.authenticate(any())).thenReturn(identity("ADMIN"));
        @SuppressWarnings("unchecked")
        ObjectProvider<WdmAdminAuthorizer> providers = mock(ObjectProvider.class);
        when(providers.stream()).thenAnswer(i -> Stream.of(new SharedAuthWdmAdminAuthorizer(authenticator)));
        var mapper = JsonMapper.builder().build();
        mvc = MockMvcBuilders.standaloneSetup(new RunResultController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter(), new ApiSecurityFilter(mapper, authenticator), new WdmRequestFilter(providers, mapper))
            .build();
    }
    static IdentityResponse identity(String role) {
        return new IdentityResponse("admin", "reader", null, "Reader", "ACTIVE", List.of(role), false);
    }
    static Metadata metadata() {
        return new Metadata(RUN, "SUCCEEDED", "COMMITTED", "WDM_RESULT_MANIFEST_V1", "a".repeat(64),
            Instant.parse("2026-11-09T10:00:00Z"), null, "1", "2", "2", "COMPLETE_VERIFIED", false, true, true);
    }
    @Test void unauthenticatedRequestIsRejectedBeforeLookup() throws Exception {
        when(authenticator.authenticate(any())).thenThrow(new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED));
        mvc.perform(get(BASE + "/patterns")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void customerCannotReadAdminPatterns() throws Exception {
        when(authenticator.authenticate(any())).thenReturn(identity("CUSTOMER"));
        mvc.perform(get(BASE + "/patterns")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void publishedPagePreservesStringIdsAndPersistedNumbers() throws Exception {
        var pattern = new PatternSummary("18446744073709551615", RUN, "<(1)(2)>", "b".repeat(64),
            "2", .5, .75, .375, "EQ2_EQ3_ARITHMETIC_MEAN_V1", 2, 2);
        when(service.page(RUN, "published", "1", "20")).thenReturn(new Page(metadata(), List.of(pattern), 1, 20, "1"));
        mvc.perform(get(BASE + "/patterns").param("view", "published").param("page", "1").param("size", "20"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().exists("X-Request-Id"))
            .andExpect(jsonPath("$.data.items[0].patternId").value("18446744073709551615"))
            .andExpect(jsonPath("$.data.items[0].canonicalPattern").value("<(1)(2)>"))
            .andExpect(jsonPath("$.data.items[0].supportAbs").value("2"))
            .andExpect(jsonPath("$.data.items[0].patternWeight").value(.375));
    }
    @Test void crossRunPatternDoesNotFallBackToAnotherRun() throws Exception {
        when(service.detail(RUN, "99", "published")).thenThrow(new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        mvc.perform(get(BASE + "/patterns/99").param("view", "published")).andExpect(status().isNotFound());
        verify(service).detail(RUN, "99", "published");
    }
    @Test void repeatedQueryParameterIsRejectedBeforeService() throws Exception {
        mvc.perform(get(BASE + "/patterns").param("view", "published", "staging")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void getBodyIsRejectedBeforeService() throws Exception {
        mvc.perform(get(BASE + "/patterns").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void literalExportRouteDoesNotBecomePatternId() throws Exception {
        when(service.export(RUN, "published")).thenReturn("{\"formatVersion\":\"WDM_RESULT_EXPORT_V2\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(get(BASE + "/patterns/export").param("view", "published"))
            .andExpect(status().isOk()).andExpect(header().exists("Content-Disposition"));
        verify(service).export(RUN, "published");
        verify(service, never()).detail(anyString(), anyString(), any());
    }
    @Test void metadataHasCorrelationAndNoCache() throws Exception {
        when(service.metadata(RUN)).thenReturn(metadata());
        mvc.perform(get(BASE + "/results")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.publicationStatus").value("COMMITTED"))
            .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
}
