package vn.edu.wspm.backend.controllers.api.v1.recommendation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.util.LinkedMultiValueMap;
import vn.edu.wspm.backend.dto.auth.IdentityResponse;
import vn.edu.wspm.backend.dto.recommendation.ComboReadResponse.Personalized;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.services.recommendation.ComboReadService;
import vn.edu.wspm.backend.web.auth.SharedRequestAuthenticator;

class PersonalComboControllerTest {
    final SharedRequestAuthenticator auth=mock(SharedRequestAuthenticator.class);
    final ComboReadService service=mock(ComboReadService.class);
    final PersonalComboController controller=new PersonalComboController(auth,service);
    final MockHttpServletRequest request=new MockHttpServletRequest();
    final MockHttpServletResponse response=new MockHttpServletResponse();
    @Test void serverIdentityOnlyAndNoStore() {
        when(auth.authenticate(request)).thenReturn(new IdentityResponse("server-user","u",null,null,"ACTIVE",List.of("CUSTOMER"),false));
        when(service.personalized("server-user")).thenReturn(new Personalized("HISTORY_PREFIX_V1",Instant.EPOCH,List.of()));
        controller.read(new LinkedMultiValueMap<>(),request,response);
        verify(service).personalized("server-user");assertEquals("no-store",response.getHeader("Cache-Control"));
    }
    @Test void authenticationPrecedesArbitraryUserIdAndDoesNotReadAnyHistory() {
        var params=new LinkedMultiValueMap<String,String>();params.add("userId","other-user");
        when(auth.authenticate(request)).thenThrow(new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED));
        assertEquals(ErrorCode.AUTHENTICATION_REQUIRED,assertThrows(ApplicationException.class,()->controller.read(params,request,response)).errorCode());
        verifyNoInteractions(service);
        doReturn(new IdentityResponse("u","u",null,null,"ACTIVE",List.of(),false)).when(auth).authenticate(request);
        assertEquals(ErrorCode.VALIDATION_FAILED,assertThrows(ApplicationException.class,()->controller.read(params,request,response)).errorCode());
        verifyNoInteractions(service);
    }

    @Test void acceptsSingleProductContextAndRejectsDuplicates() {
        doReturn(new IdentityResponse("u","u",null,null,"ACTIVE",List.of(),false)).when(auth).authenticate(request);
        var params=new LinkedMultiValueMap<String,String>();params.add("productId","p");
        when(service.personalized("u","p")).thenReturn(new Personalized("HISTORY_PREFIX_V1",Instant.EPOCH,List.of()));
        controller.read(params,request,response);verify(service).personalized("u","p");
        params.add("productId","q");
        assertThrows(ApplicationException.class,()->controller.read(params,request,response));
        verifyNoMoreInteractions(service);
    }
    @Test void exactPopupUsesAuthenticatedIdentityAndRejectsAmbiguousContext() {
        doReturn(new IdentityResponse("owner","u",null,null,"ACTIVE",List.of(),false)).when(auth).authenticate(request);
        var params = new LinkedMultiValueMap<String,String>(); params.add("comboId","c");
        when(service.personalizedCombo("owner","c")).thenReturn(new Personalized("HISTORY_PREFIX_V1",Instant.EPOCH,List.of()));
        controller.read(params,request,response);
        verify(service).personalizedCombo("owner","c");
        assertEquals("no-store",response.getHeader("Cache-Control"));
        params.add("productId","p");
        assertThrows(ApplicationException.class,()->controller.read(params,request,response));
        params.remove("productId"); params.add("comboId","other");
        assertThrows(ApplicationException.class,()->controller.read(params,request,response));
        verifyNoMoreInteractions(service);
    }
}
