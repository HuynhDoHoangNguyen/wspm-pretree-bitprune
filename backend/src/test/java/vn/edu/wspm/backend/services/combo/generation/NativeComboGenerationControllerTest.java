package vn.edu.wspm.backend.services.combo.generation;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import vn.edu.wspm.backend.controllers.api.v1.combo.NativeComboGenerationController;
import vn.edu.wspm.backend.web.auth.*;
import java.util.List;
class NativeComboGenerationControllerTest {
    final ComboGenerationService service=mock(ComboGenerationService.class);
    final NativeComboGenerationController controller=new NativeComboGenerationController(service,new AdminPrincipalResolver(),new tools.jackson.databind.ObjectMapper());
    @Test void noClientActorApprovalPriceOrAnonymousCommandReachesGeneration(){
        var r=new MockHttpServletRequest();assertThrows(RuntimeException.class,()->controller.preview("{}",r));
        r.setUserPrincipal(new AdminPrincipal("customer",List.of("CUSTOMER")));assertThrows(RuntimeException.class,()->controller.preview("{}",r));
        r.setUserPrincipal(new AdminPrincipal("admin",List.of("ADMIN")));
        assertThrows(RuntimeException.class,()->controller.preview("{\"runId\":\"run\",\"actor\":\"forged\"}",r));
        assertThrows(RuntimeException.class,()->controller.submit("{\"contextToken\":\"token\",\"price\":0}",r));
        verifyNoInteractions(service);
    }
    @Test void passesOnlyWhitelistedCommandAndCurrentPrincipal(){
        var r=new MockHttpServletRequest();var actor=new AdminPrincipal("admin",List.of("ADMIN"));r.setUserPrincipal(actor);
        controller.preview("{\"runId\":\"run\",\"anchorProductId\":\"laptop\",\"k\":5,\"requestIdentity\":\"id\",\"selection\":{\"requiredItems\":[{\"itemId\":12,\"quantity\":3}],\"minDistinctProducts\":2,\"maxDistinctProducts\":5}}",r);
        verify(service).previewSelected("run","laptop",5,"id",actor,new ProductSelection(List.of(new ProductSelection.RequiredItem(12,3)),2,5));
        controller.reconcile("{\"contextToken\":\"signed\"}",r);verify(service).writePublished("signed",actor,true);
        verifyNoMoreInteractions(service);
    }
    @Test void rejectsFractionalQuantityUnknownNestedFieldsAndMissingSelection(){
        var r=new MockHttpServletRequest();r.setUserPrincipal(new AdminPrincipal("admin",List.of("ADMIN")));
        for(String q:List.of("1.5","1.0","\"2\"","null","0","4294967296")){
            String body="{\"selection\":{\"requiredItems\":[{\"itemId\":12,\"quantity\":"+q+"}],\"minDistinctProducts\":2,\"maxDistinctProducts\":4}}";
            assertThrows(RuntimeException.class,()->controller.preview(body,r));
        }
        assertThrows(RuntimeException.class,()->controller.preview("{}",r));verifyNoInteractions(service);
    }
    @Test void optionsChecksCurrentAdminBeforeResolvingDictionary()throws Exception{
        var request=new MockHttpServletRequest();assertThrows(RuntimeException.class,()->controller.options("run",request));
        request.setUserPrincipal(new AdminPrincipal("customer",List.of("CUSTOMER")));assertThrows(RuntimeException.class,()->controller.options("run",request));
        verifyNoInteractions(service);
        var actor=new AdminPrincipal("admin",List.of("ADMIN"));request.setUserPrincipal(actor);
        controller.options("run",request);verify(service).selectionOptions("run",actor);
    }
    @Test void httpOptionsBindsRunPathWithoutCompilerParameterNames()throws Exception{
        var actor=new AdminPrincipal("admin",List.of("ADMIN"));String run="10000000-0000-4000-8000-000000000001";
        when(service.selectionOptions(run,actor)).thenReturn(new NativeComboGeneration.Options(run,ProductSelection.VERSION,4000,4294967295L,4000,List.of()));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/combo-generation/options/"+run).principal(actor))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.runId").value(run));
        verify(service).selectionOptions(run,actor);
    }
    @Test void categoryCommandIsExactAndRejectsMixedProductSelection(){
        var actor=new AdminPrincipal("admin",List.of("ADMIN"));var request=new MockHttpServletRequest();request.setUserPrincipal(actor);
        String body="{\"runId\":\"run\",\"requestIdentity\":\"category\",\"categorySelection\":{\"requiredCategories\":[{\"categoryId\":\"C\",\"quantity\":2}],\"minDistinctProducts\":2,\"maxDistinctProducts\":4}}";
        controller.preview(body,request);
        verify(service).previewCategories("run",null,null,"category",actor,new CategorySelection(List.of(new CategorySelection.RequiredCategory("C",2)),2,4));
        assertThrows(RuntimeException.class,()->controller.preview(body.replace("\"categorySelection\":","\"selection\":{},\"categorySelection\":"),request));
        assertThrows(RuntimeException.class,()->controller.preview(body.replace("\"quantity\":2","\"quantity\":1.5"),request));
        verifyNoMoreInteractions(service);
    }
}
