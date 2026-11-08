package vn.edu.wspm.backend.controllers.api.v1.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.dto.run.result.RunResultResponse.*;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.services.run.query.RunResultQueryService;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.web.wdm.*;

class RunResultControllerTest {
    static final String RUN = "10000000-0000-4000-8000-000000000001", PATH = "/api/v1/admin/mining-runs/" + RUN;
    final RunResultQueryService service = mock(RunResultQueryService.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        var beans = new StaticListableBeanFactory();
        beans.addBean("guard", (WdmAdminAuthorizer) request -> {
            if (request.getHeader("X-Test-Identity") == null) throw new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED);
            if (!"ADMIN".equals(request.getHeader("X-Test-Identity"))) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        });
        mvc = MockMvcBuilders.standaloneSetup(new RunResultController(service)).setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter(), new WdmRequestFilter(beans.getBeanProvider(WdmAdminAuthorizer.class), JsonMapper.builder().build())).build();
    }
    @Test void allRoutesAuthorizeBeforeValidationAndLookup() throws Exception {
        for (String suffix : List.of("/results", "/patterns", "/patterns/1", "/patterns/export")) {
            mvc.perform(get(PATH + suffix + "?bad=1").content("private")).andExpect(status().isUnauthorized());
            mvc.perform(get(PATH + suffix).header("X-Test-Identity", "CUSTOMER")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void metadataAndPageUseEnvelopeAndRequestId() throws Exception {
        when(service.page(RUN, "staging", "2", "1")).thenReturn(new Page(null, List.of(), 2, 1, "9007199254740993"));
        var result = mvc.perform(get(PATH + "/patterns?view=staging&page=2&size=1").header("X-Test-Identity", "ADMIN"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.data.total").value("9007199254740993")).andReturn();
        assertEquals(result.getResponse().getHeader("X-Request-Id"), JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString()).get("requestId").asString());
        mvc.perform(get(PATH + "/results").header("X-Test-Identity", "ADMIN")).andExpect(status().isOk());
        verify(service).metadata(RUN);
    }
    @Test void detailPreservesUnsignedPatternId() throws Exception {
        mvc.perform(get(PATH + "/patterns/18446744073709551615?view=staging").header("X-Test-Identity", "ADMIN"))
                .andExpect(status().isOk());
        verify(service).detail(RUN, "18446744073709551615", "staging");
    }
    @Test void downloadLiteralRouteHasPriorityAndReturnsRawVersionedFile() throws Exception {
        byte[] bytes = "{\"formatVersion\":\"WDM_RESULT_EXPORT_V1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(service.export(RUN, "staging")).thenReturn(bytes);
        mvc.perform(get(PATH + "/patterns/export?view=staging").header("X-Test-Identity", "ADMIN"))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"mining-result-" + RUN + ".json\""))
                .andExpect(header().string("Content-Length", Integer.toString(bytes.length)))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(service, never()).detail(any(), any(), any());
    }
    @Test void duplicateUnknownQueryBodyMediaAndMethodAreRejected() throws Exception {
        for (String suffix : List.of("/patterns?view=staging&view=staging", "/patterns?sort=bad", "/results?view=staging", "/patterns/export?page=1"))
            mvc.perform(get(PATH + suffix).header("X-Test-Identity", "ADMIN")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH + "/patterns").header("X-Test-Identity", "ADMIN").content(" ")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH + "/patterns").header("X-Test-Identity", "ADMIN").accept("text/csv")).andExpect(status().isNotAcceptable());
        mvc.perform(post(PATH + "/patterns").header("X-Test-Identity", "ADMIN")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }
    @Test void failedDownloadHasJsonErrorAndNoAttachmentOrPartialFile() throws Exception {
        for (var code : List.of(ErrorCode.INVALID_RESOURCE_STATE, ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.PAYLOAD_TOO_LARGE, ErrorCode.SERVICE_UNAVAILABLE)) {
            doThrow(new ApplicationException(code)).when(service).export(RUN, null);
            mvc.perform(get(PATH + "/patterns/export").header("X-Test-Identity", "ADMIN"))
                    .andExpect(status().is(code.status().value())).andExpect(jsonPath("$.code").value(code.name()))
                    .andExpect(header().doesNotExist("Content-Disposition"));
        }
    }
}
