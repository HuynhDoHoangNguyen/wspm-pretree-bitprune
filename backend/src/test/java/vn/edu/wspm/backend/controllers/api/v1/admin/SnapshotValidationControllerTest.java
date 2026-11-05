package vn.edu.wspm.backend.controllers.api.v1.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.exception.GlobalExceptionHandler;
import vn.edu.wspm.backend.services.dataset.validation.*;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.web.wdm.WdmAdminAuthorizer;
import vn.edu.wspm.backend.web.wdm.WdmRequestFilter;

import java.time.Clock;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Mock service + test authorizer; exercises MVC and WDM guard, not shared Auth verification. */
class SnapshotValidationControllerTest {
    private static final String ID = "00000000-0000-0000-0000-000000000001";
    private static final String PATH = "/api/v1/admin/dataset-snapshots/" + ID + "/validation";
    private final SnapshotValidationService service = mock(SnapshotValidationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() throws Exception {
        var factory = new StaticListableBeanFactory();
        factory.addBean("authorizer", (WdmAdminAuthorizer) request -> {
            String identity = request.getHeader("X-Test-Identity");
            if (identity == null) throw new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED);
            if (!identity.equals("ADMIN")) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        });
        mvc = MockMvcBuilders.standaloneSetup(new SnapshotValidationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter(), new WdmRequestFilter(factory.getBeanProvider(WdmAdminAuthorizer.class),
                        JsonMapper.builder().build())).build();
        var limits = new ValidationLimits(16384, 100, 8388608, 16777216, 30000, 2);
        var invalid = new ValidationReportEngine(ids -> Set.of(), limits, Clock.systemUTC())
                .generate(ID, "a".repeat(64), "1", new byte[]{(byte) 0xff}, () -> {});
        when(service.validate(ID)).thenReturn(invalid);
        when(service.read(ID)).thenReturn(invalid);
    }

    @Test
    void invalidDataIsSuccessfulOperationAndPublicProjectionOmitsManifest() throws Exception {
        mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.validationStatus").value("INVALID"))
                .andExpect(jsonPath("$.data.countsComplete").value(false))
                .andExpect(jsonPath("$.data.manifest").doesNotExist())
                .andExpect(jsonPath("$.data.issues.content[0].code").value("CSV_INVALID_UTF8"));
    }

    @Test
    void guardRejectsBeforeRequestValidationOrServiceAccess() throws Exception {
        mvc.perform(post(PATH).content("private-input")).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("X-Test-Identity", "CUSTOMER").param("page", "bad"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsUnknownDuplicateNegativeAndOversizedQueryAndPostBodies() throws Exception {
        for (var request : java.util.List.of(get(PATH).param("page", "-1"), get(PATH).param("size", "0"),
                get(PATH).param("size", "101"), get(PATH).param("page", "1", "2"), get(PATH).param("unknown", "x"),
                get(PATH).param("page", "999999999999"), post(PATH).param("force", "true"), post(PATH).content("{}"))) {
            mvc.perform(request.header("X-Test-Identity", "ADMIN")).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void pageBeyondEndAndPendingStateAreExplicit() throws Exception {
        mvc.perform(get(PATH).header("X-Test-Identity", "ADMIN").param("page", "2147483647"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.issues.content").isEmpty());
        when(service.read(ID)).thenThrow(new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE));
        mvc.perform(get(PATH).header("X-Test-Identity", "ADMIN")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESOURCE_STATE"));
    }
}
