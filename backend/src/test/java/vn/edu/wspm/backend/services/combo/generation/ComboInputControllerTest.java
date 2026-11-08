package vn.edu.wspm.backend.services.combo.generation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import vn.edu.wspm.backend.controllers.api.v1.combo.ComboInputController;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.web.auth.AdminPrincipal;
import vn.edu.wspm.backend.web.auth.AdminPrincipalResolver;

class ComboInputControllerTest {
    final PublishedComboInputService service = mock(PublishedComboInputService.class);
    final ComboInputController controller = new ComboInputController(service,new AdminPrincipalResolver());
    @Test void anonymousAndCustomerCannotReachTheProducer() {
        var request = new MockHttpServletRequest();
        assertThrows(ApplicationException.class,()->controller.input("run",request));
        request.setUserPrincipal(new AdminPrincipal("customer",List.of("CUSTOMER")));
        assertThrows(ApplicationException.class,()->controller.input("run",request));
        verifyNoInteractions(service);
    }
    @Test void adminReadsOnlyTheRequestedRunAndCannotSupplyApprovalFields() throws Exception {
        var request = new MockHttpServletRequest();
        request.setUserPrincipal(new AdminPrincipal("admin",List.of("ADMIN")));
        controller.input("run",request);
        verify(service).summary("run");
        request.addParameter("approved","true");
        assertThrows(ApplicationException.class,()->controller.input("run",request));
        verifyNoMoreInteractions(service);
    }
}
