package vn.edu.wspm.backend.controllers.api.v1.combo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.wspm.backend.services.combo.review.ComboPublicationService;
import vn.edu.wspm.backend.web.auth.*;

class ComboPublicationControllerTest {
    private final ComboPublicationService service = mock(ComboPublicationService.class);
    private final ComboPublicationController controller = new ComboPublicationController(service, new AdminPrincipalResolver());
    @Test void rejectsAnonymousCustomerAndClientSuppliedData() {
        var request = new MockHttpServletRequest();
        assertThrows(RuntimeException.class, () -> controller.publish("B1", request));
        request.setUserPrincipal(new AdminPrincipal("customer", List.of("CUSTOMER")));
        assertThrows(RuntimeException.class, () -> controller.publish("B1", request));
        request.setUserPrincipal(new AdminPrincipal("admin", List.of("ADMIN")));
        request.setContent("{\"status\":\"APPROVED\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(RuntimeException.class, () -> controller.publish("B1", request));
        verifyNoInteractions(service);
    }
    @Test void explicitPostBindsBatchAndAuthenticatedPrincipal() throws Exception {
        var actor = new AdminPrincipal("admin", List.of("ADMIN"));
        when(service.publish("B1", actor)).thenReturn(new ComboPublicationService.Publication("B1", "ACTIVE", 2));
        MockMvcBuilders.standaloneSetup(controller).build()
            .perform(post("/api/v1/admin/combos/batches/B1/publication").principal(actor))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.batchId").value("B1"))
            .andExpect(jsonPath("$.data.publishedCount").value(2));
        verify(service).publish("B1", actor);
    }
}
