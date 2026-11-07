package vn.edu.wspm.backend.controllers.api.v1.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.dto.run.RunStatusResponse;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.services.run.RunStatusService;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.web.wdm.*;

class RunStatusControllerTest {
    static final String ID = "550e8400-e29b-41d4-a716-446655440000", PATH = "/api/v1/admin/mining-runs/" + ID;
    final RunStatusService service = mock(RunStatusService.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        var beans = new StaticListableBeanFactory();
        beans.addBean("guard", (WdmAdminAuthorizer) request -> {
            if (request.getHeader("X-Test-Identity") == null) throw new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED);
            if (!"ADMIN".equals(request.getHeader("X-Test-Identity"))) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        });
        mvc = MockMvcBuilders.standaloneSetup(new RunStatusController(service)).setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter(), new WdmRequestFilter(beans.getBeanProvider(WdmAdminAuthorizer.class), JsonMapper.builder().build())).build();
    }
    @Test void guardPrecedesQueryBodyAndLookup() throws Exception {
        mvc.perform(get(PATH + "?bad=1").content("secret")).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("X-Test-Identity", "CUSTOMER").content("secret")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void successUsesReadEnvelopeHeadersAndNoCreationLocation() throws Exception {
        when(service.read(ID)).thenReturn(new RunStatusResponse(ID, ID, "WSPM_PRETREE", "QUEUED", null,
                java.time.Instant.parse("2026-11-07T05:00:00.123456Z"), null, null, null, null, null, null, null));
        var result = mvc.perform(get(PATH).header("X-Test-Identity", "ADMIN")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().doesNotExist("Location")).andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-07T05:00:00.123456Z")).andReturn();
        var json = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
        assertEquals(result.getResponse().getHeader("X-Request-Id"), json.get("requestId").asString());
    }
    @Test void queryBodyMediaAndUnsupportedMethodDoNotReachService() throws Exception {
        mvc.perform(get(PATH + "?force=1").header("X-Test-Identity", "ADMIN")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).header("X-Test-Identity", "ADMIN").content(" ")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).header("X-Test-Identity", "ADMIN").accept("text/plain")).andExpect(status().isNotAcceptable());
        mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN")).andExpect(status().isMethodNotAllowed()); verifyNoInteractions(service);
    }
    @Test void safeServiceErrorsUseStandardStatusEnvelope() throws Exception {
        for (var error : new ErrorCode[]{ErrorCode.VALIDATION_FAILED, ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.SERVICE_UNAVAILABLE}) {
            doThrow(new ApplicationException(error)).when(service).read(ID);
            mvc.perform(get(PATH).header("X-Test-Identity", "ADMIN")).andExpect(status().is(error.status().value()))
                    .andExpect(jsonPath("$.code").value(error.name())).andExpect(header().string("Cache-Control", "no-store"));
        }
    }
}
