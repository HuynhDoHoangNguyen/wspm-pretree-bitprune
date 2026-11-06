package vn.edu.wspm.backend.web.wdm;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.wspm.backend.exception.GlobalExceptionHandler;
import vn.edu.wspm.backend.web.common.RequestIdFilter;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real reader/validator/error boundary; test-only route, without Auth, DB or execution. */
class RunRequestContractWebTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final ContractController controller = new ContractController(new RunRequestReader(FACTORY.getValidator()));
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter()).build();

    @AfterAll static void closeFactory() { FACTORY.close(); }

    @Test void acceptsNewRequestThroughTheActualReader() throws Exception {
        mvc.perform(post("/api/test-run-contract").contentType(MediaType.APPLICATION_JSON)
                .content(RunRequestReaderTest.BODY)).andExpect(status().isOk());
        assertEquals(1, controller.accepted);
    }

    @Test void fieldErrorsUseSafeEnvelopeAndMatchingRequestId() throws Exception {
        var result = mvc.perform(post("/api/test-run-contract").contentType(MediaType.APPLICATION_JSON)
                        .content(RunRequestReaderTest.BODY.replace("10.0", "-12345")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path").value("/api/test-run-contract"))
                .andExpect(jsonPath("$.details[0].field").value("minsupValue"))
                .andExpect(jsonPath("$.details[0].code").value("INVALID_VALUE"))
                .andExpect(jsonPath("$.details[0].message").value("Supply a valid value."))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.requestId").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString())))
                .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains(result.getResponse().getHeader("X-Request-Id")));
        assertFalse(result.getResponse().getContentAsString().contains("-12345"));
        assertEquals(0, controller.accepted);
    }

    @Test void malformedJsonUsesCommonEnvelopeBeforeAcceptance() throws Exception {
        var result = mvc.perform(post("/api/test-run-contract").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains(result.getResponse().getHeader("X-Request-Id")));
        assertEquals(0, controller.accepted);
    }

    @Test void legacyFieldIsRejectedBeforeAcceptance() throws Exception {
        mvc.perform(post("/api/test-run-contract").contentType(MediaType.APPLICATION_JSON)
                        .content(RunRequestReaderTest.BODY.replace("}", ",\"minWeight\":1}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("body"));
        assertEquals(0, controller.accepted);
    }

    @RestController
    static class ContractController {
        private final RunRequestReader reader;
        private int accepted;
        ContractController(RunRequestReader reader) { this.reader = reader; }
        @PostMapping("/api/test-run-contract")
        public void read(HttpServletRequest request) throws IOException {
            reader.read(request.getInputStream());
            accepted++;
        }
    }
}
