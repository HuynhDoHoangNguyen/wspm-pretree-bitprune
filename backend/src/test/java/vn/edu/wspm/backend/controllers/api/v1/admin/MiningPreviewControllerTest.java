package vn.edu.wspm.backend.controllers.api.v1.admin;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.dto.run.*;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.services.run.MiningPreviewService;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.web.wdm.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MiningPreviewControllerTest {
    private static final String PATH = "/api/v1/admin/mining-runs/preview";
    private static final String BODY = "{\"snapshotId\":\"550e8400-e29b-41d4-a716-446655440000\",\"algorithm\":\"WSPM_PRETREE\",\"minsupMode\":\"PERCENT\",\"minsupValue\":14}";
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final MiningPreviewService service = mock(MiningPreviewService.class);
    private MockMvc mvc;
    @AfterAll static void close() { FACTORY.close(); }
    @BeforeEach void setup() {
        var beans = new StaticListableBeanFactory();
        beans.addBean("authorizer", (WdmAdminAuthorizer) req -> {
            if (req.getHeader("X-Test-Identity") == null) throw new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED);
            if (!req.getHeader("X-Test-Identity").equals("ADMIN")) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        });
        mvc = MockMvcBuilders.standaloneSetup(new MiningPreviewController(new RunRequestReader(FACTORY.getValidator()), service))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter(),
                        new WdmRequestFilter(beans.getBeanProvider(WdmAdminAuthorizer.class), JsonMapper.builder().build())).build();
    }
    @Test void authorizationPrecedesMalformedJsonTransportAndLookup() throws Exception {
        mvc.perform(post(PATH).contentType("text/plain").content("{")) .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header("X-Test-Identity", "CUSTOMER").contentType("application/json").content("{"))
                .andExpect(status().isForbidden()); verifyNoInteractions(service);
    }
    @Test void successUsesEnvelopeMatchingRequestIdAndNoStore() throws Exception {
        when(service.preview(any())).thenReturn(new MiningPreviewResponse("id", "WSPM_PRETREE", "v", "a", "b", null,
                new MiningPreviewResponse.MinimumSupport("PERCENT", 14, 0.14, 0.42), "NOT_EVALUATED"));
        var result = mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.executionEligibility").value("NOT_EVALUATED"))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("X-Content-Type-Options", "nosniff")).andReturn();
        assertTrue(result.getResponse().getContentAsString().contains(result.getResponse().getHeader("X-Request-Id")));
        verify(service).preview(new RunCreateRequest("550e8400-e29b-41d4-a716-446655440000", "WSPM_PRETREE", "PERCENT", 14.0));
    }
    @Test void rejectsInvalidJsonLegacyInputsAndQueryBeforeService() throws Exception {
        for (String body : java.util.List.of("{", BODY + " {}", BODY.replace("}", ",\"minsupValue\":2}"),
                BODY.replace("}", ",\"sequenceCount\":3}"), BODY.replace("}", ",\"minWeight\":1}"))) {
            mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").param("force", "true").contentType("application/json").content(BODY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details[0].field").value("query"));
        verifyNoInteractions(service);
    }
    @Test void methodAndMediaAreRestricted() throws Exception {
        mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").contentType("text/plain").content(BODY)).andExpect(status().isUnsupportedMediaType());
        mvc.perform(get(PATH).header("X-Test-Identity", "ADMIN")).andExpect(status().isMethodNotAllowed()); verifyNoInteractions(service);
    }
    @Test void preservesServiceErrorEnvelopeWithoutRawInput() throws Exception {
        when(service.preview(any())).thenThrow(new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE));
        var result = mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").contentType("application/json").content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_RESOURCE_STATE"))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        assertTrue(result.getResponse().getContentAsString().contains(result.getResponse().getHeader("X-Request-Id")));
    }
}
