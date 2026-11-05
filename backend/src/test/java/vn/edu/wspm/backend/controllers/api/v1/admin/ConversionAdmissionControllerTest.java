package vn.edu.wspm.backend.controllers.api.v1.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionValidationGate;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.web.wdm.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MVC contract with a test authorizer; real shared Auth is covered by browser integration. */
class ConversionAdmissionControllerTest {
    private static final String ID = "00000000-0000-0000-0000-000000000001";
    private static final String PATH = "/api/v1/admin/dataset-snapshots/" + ID + "/conversion";
    private final ConversionValidationGate gate = mock(ConversionValidationGate.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        var factory = new StaticListableBeanFactory();
        factory.addBean("authorizer", (WdmAdminAuthorizer) request -> {
            String identity = request.getHeader("X-Test-Identity");
            if (identity == null) throw new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED);
            if (!identity.equals("ADMIN")) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        });
        mvc = MockMvcBuilders.standaloneSetup(new ConversionAdmissionController(gate))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter(), new WdmRequestFilter(factory.getBeanProvider(WdmAdminAuthorizer.class),
                        JsonMapper.builder().build())).build();
    }

    @Test void authorizationPrecedesInputAndGate() throws Exception {
        mvc.perform(post(PATH.replace(ID, "bad")).content("private-input")).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH.replace(ID, "bad")).header("X-Test-Identity", "CUSTOMER").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(gate);
    }

    @Test void rejectsBodyAndQueryBeforeGate() throws Exception {
        mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").param("force", "true"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(gate);
    }

    @Test void ineligibleSnapshotIsConflict() throws Exception {
        when(gate.prepare(ID)).thenThrow(new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE));
        mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESOURCE_STATE"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void eligibleSnapshotDoesNotPretendConversionStarted() throws Exception {
        mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Conversion is not available yet."))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(gate).prepare(ID);
    }
}
