package vn.edu.wspm.backend.controllers.api.v1.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.dto.run.RunAcceptedResponse;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.services.run.RunCreationService;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.web.wdm.*;

/** Standalone MVC contract with test authorizer; actual shared Auth is tested in MySQL integration. */
class RunCreationControllerTest {
    static final String PATH = "/api/v1/admin/mining-runs";
    static final String ID = "550e8400-e29b-41d4-a716-446655440000";
    static final String BODY = "{\"snapshotId\":\"" + ID + "\",\"algorithm\":\"WSPM_PRETREE\",\"minsupMode\":\"PERCENT\",\"minsupValue\":14}";
    static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    final RunCreationService service = mock(RunCreationService.class);
    MockMvc mvc;
    @AfterAll static void close() { FACTORY.close(); }
    @BeforeEach void setup() {
        var beans = new StaticListableBeanFactory();
        beans.addBean("guard", (WdmAdminAuthorizer) request -> {
            if (request.getHeader("X-Test-Identity") == null) throw new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED);
            if (!"ADMIN".equals(request.getHeader("X-Test-Identity"))) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        });
        mvc = MockMvcBuilders.standaloneSetup(new RunCreationController(new RunRequestReader(FACTORY.getValidator()), service))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter(),
                        new WdmRequestFilter(beans.getBeanProvider(WdmAdminAuthorizer.class), JsonMapper.builder().build())).build();
    }
    @Test void authPrecedesTransportParsingAndService() throws Exception {
        mvc.perform(post(PATH).contentType("text/plain").content("{")).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header("X-Test-Identity", "CUSTOMER").contentType("application/json").content("{"))
                .andExpect(status().isForbidden()); verifyNoInteractions(service);
    }
    @Test void acceptedUsesClosedEnvelopeLocationAndUtcTimestamp() throws Exception {
        when(service.create(any())).thenReturn(new RunAcceptedResponse(ID, ID, "WSPM_PRETREE", "QUEUED", Instant.parse("2026-11-07T05:00:00.123456Z")));
        var result = mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").contentType("application/json").content(BODY))
                .andExpect(status().isAccepted()).andExpect(header().string("Location", PATH + "/" + ID))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.runId").value(ID)).andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-07T05:00:00.123456Z")).andReturn();
        var json = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
        assertEquals(result.getResponse().getHeader("X-Request-Id"), json.get("requestId").asString());
        assertEquals(java.util.Set.of("runId", "snapshotId", "algorithm", "status", "createdAt"), java.util.Set.copyOf(json.get("data").propertyNames()));
    }
    @Test void strictReaderQueryAndMediaErrorsNeverReachService() throws Exception {
        for (String body : new String[]{"{", BODY.replace("14}", "14,\"extra\":1}"), BODY.replace("14}", "null}")}) {
            mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(PATH + "?force=true").header("X-Test-Identity", "ADMIN").contentType("application/json").content(BODY)).andExpect(status().isBadRequest());
        mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").contentType("text/plain").content(BODY)).andExpect(status().isUnsupportedMediaType());
        mvc.perform(get(PATH).header("X-Test-Identity", "ADMIN")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }
    @Test void applicationErrorsKeepStatusNoLocationAndSafeEnvelope() throws Exception {
        for (var code : new ErrorCode[]{ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.INVALID_RESOURCE_STATE,
                ErrorCode.UNSUPPORTED_MINING_CONFIGURATION, ErrorCode.SERVICE_UNAVAILABLE}) {
            doThrow(new ApplicationException(code)).when(service).create(any());
            var result = mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").contentType("application/json").content(BODY))
                    .andExpect(status().is(code.status().value())).andExpect(jsonPath("$.code").value(code.name()))
                    .andExpect(header().doesNotExist("Location")).andExpect(header().string("Cache-Control", "no-store")).andReturn();
            var json = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
            assertEquals(result.getResponse().getHeader("X-Request-Id"), json.get("requestId").asString());
        }
    }
}
