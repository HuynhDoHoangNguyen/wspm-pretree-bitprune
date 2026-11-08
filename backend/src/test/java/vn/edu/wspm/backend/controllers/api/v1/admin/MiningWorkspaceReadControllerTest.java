package vn.edu.wspm.backend.controllers.api.v1.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.services.workspace.MiningWorkspaceReadService;
import vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.web.wdm.*;

class MiningWorkspaceReadControllerTest {
    final MiningWorkspaceReadService service = mock(MiningWorkspaceReadService.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        var beans = new StaticListableBeanFactory();
        beans.addBean("guard", (WdmAdminAuthorizer) request -> {
            if (request.getHeader("X-Test-Identity") == null) throw new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED);
            if (!"ADMIN".equals(request.getHeader("X-Test-Identity"))) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        });
        mvc = MockMvcBuilders.standaloneSetup(new MiningWorkspaceReadController(service, beans.getBeanProvider(ManagedRunExecutionAdmission.class)))
            .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter(), new WdmRequestFilter(beans.getBeanProvider(WdmAdminAuthorizer.class), JsonMapper.builder().build())).build();
    }
    @Test void authorizationPrecedesParsingOnEveryNewEndpoint() throws Exception {
        for (String path : List.of("dataset-snapshots", "mining-runs", "mining-execution/capacity", "dataset-snapshots/bad/storage")) {
            mvc.perform(get("/api/v1/admin/" + path + "?unknown=1").content("bad")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/admin/" + path).header("X-Test-Identity", "CUSTOMER")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void rejectsUnknownDuplicateAndBodyBeforeQuerying() throws Exception {
        for (String suffix : List.of("?bad=1", "?page=1&page=2")) mvc.perform(get("/api/v1/admin/mining-runs" + suffix).header("X-Test-Identity", "ADMIN")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/dataset-snapshots").header("X-Test-Identity", "ADMIN").content(" ")).andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void disabledCapacityIsUnknownRatherThanFabricatedFreeSlots() throws Exception {
        mvc.perform(get("/api/v1/admin/mining-execution/capacity").header("X-Test-Identity", "ADMIN"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.enabled").value(false)).andExpect(jsonPath("$.data.scope").value("CURRENT_BACKEND_INSTANCE"))
            .andExpect(jsonPath("$.data.snapshot").isEmpty()); verifyNoInteractions(service);
    }
    @Test void storageBindsExplicitPathParameterWithoutCompilerParameterMetadata() throws Exception {
        String id = "10000000-0000-4000-8000-000000000001";
        when(service.storage(id)).thenReturn(new MiningWorkspaceReadService.Storage(id, "orders.csv", "C:\\storage", "CURRENT_SNAPSHOT_PUBLICATION", "METADATA_ONLY_NOT_INTEGRITY_VERIFIED", List.of()));
        mvc.perform(get("/api/v1/admin/dataset-snapshots/" + id + "/storage").header("X-Test-Identity", "ADMIN"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.snapshotId").value(id));
        verify(service).storage(id);
    }
    @Test void listUsesEnvelopeAndDoesNotExposeRawExceptions() throws Exception {
        when(service.list(eq(false), anyMap())).thenReturn(new MiningWorkspaceReadService.Page(List.of(), 1, 20, 0));
        mvc.perform(get("/api/v1/admin/dataset-snapshots").header("X-Test-Identity", "ADMIN"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0)).andExpect(jsonPath("$.data.items").isArray());
        assertEquals(20, MiningWorkspaceReadService.integer(null, 20, 1, 100));
        for (String v : List.of("-1", "0", "101", "abc", "99999999")) assertThrows(ApplicationException.class, () -> MiningWorkspaceReadService.integer(v, 20, 1, 100));
    }
}
