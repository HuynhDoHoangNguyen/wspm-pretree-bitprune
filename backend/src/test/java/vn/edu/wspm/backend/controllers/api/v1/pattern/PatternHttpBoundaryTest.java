package vn.edu.wspm.backend.controllers.api.v1.pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.dto.pattern.PatternFilterInput;
import vn.edu.wspm.backend.exception.GlobalExceptionHandler;
import vn.edu.wspm.backend.services.pattern.*;
import vn.edu.wspm.backend.web.auth.AdminPrincipal;
import vn.edu.wspm.backend.web.auth.AdminPrincipalResolver;

/** Boundary unit tests; real servlet filters and JDBC are tested separately. */
class PatternHttpBoundaryTest {
    @Test
    void allReaderCodesUseCentralErrorEnvelopeAndOnlySafeMembershipDetails() throws Exception {
        for(var code:PatternReadException.Code.values()) {
            var request=new MockHttpServletRequest("GET","/api/v1/admin/mining-runs/r/patterns");
            var error=new PatternReadException(code,code==PatternReadException.Code.ITEM_NOT_IN_RUN_DICTIONARY?List.of(2,3):List.of());
            var response=new GlobalExceptionHandler().handle(error,request,new MockHttpServletResponse());
            int expected=switch(code) {
                case RUN_NOT_FOUND,PATTERN_NOT_FOUND -> 404;
                case RESULT_INCONSISTENT,RESULTS_NOT_AVAILABLE,WEIGHT_FILTER_UNAVAILABLE -> 409;
                default -> 400;
            };
            assertEquals(expected,response.getStatusCode().value());
            assertEquals(code.name(),response.getBody().code());
            assertEquals(code==PatternReadException.Code.ITEM_NOT_IN_RUN_DICTIONARY?2:0,response.getBody().details().size());
        }
    }

    @Test
    void transportKeepsRawStringsAndUnsignedDetailIdWithoutIndependentReads() {
        var lists=mock(PatternListService.class);var details=mock(PatternDetailService.class);
        var dictionary=mock(DictionaryService.class);
        var controller=new PatternReadController(lists,details,dictionary,new AdminPrincipalResolver());
        var request=new MockHttpServletRequest();request.setUserPrincipal(new AdminPrincipal("admin",List.of("ADMIN","CUSTOMER")));
        request.addParameter("itemId","1","1","2");request.addParameter("supportMin","9007199254740993");
        controller.list("r",request);
        verify(lists).list("r",null,null,new PatternFilterInput(List.of("1","1","2"),"9007199254740993",null,null,null));
        verifyNoInteractions(details,dictionary);
        request.removeAllParameters();
        controller.detail("r","18446744073709551615",request);
        verify(details).detail("r","18446744073709551615");
        request.addParameter("q","");controller.dictionary("r",request);
        verify(dictionary).search("r","",null,null);
    }

    @Test
    void databaseFailureIsSafeServerErrorNotEmptySuccess() throws Exception {
        var request=new MockHttpServletRequest("GET","/api/v1/admin/mining-runs/r/patterns");
        var result=new GlobalExceptionHandler().handle(new DataAccessResourceFailureException("private-password-sql"),request,new MockHttpServletResponse());
        assertEquals(503,result.getStatusCode().value());
        assertFalse(JsonMapper.builder().build().writeValueAsString(result.getBody()).contains("private-password-sql"));
    }
}
