package vn.edu.wspm.backend.web.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import vn.edu.wspm.backend.exception.GlobalExceptionHandler;

/** Standalone MVC boundary test; the endpoint is test-only, not an application route. */
class ApiValidationTest {
    private final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(new ValidationController())
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .addFilters(new RequestIdFilter())
                    .build();

    @Test
    void validationDetailsAreSafeAndSortedAndMalformedJsonUsesTheSameEnvelope() throws Exception {
        mvc.perform(
                        post("/api/test-validation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"\",\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("email"))
                .andExpect(jsonPath("$.details[1].field").value("username"))
                .andExpect(jsonPath("$.details[0].message").value("Supply a valid value."))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(
                        post("/api/test-validation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void unsupportedMethodRetainsAllowAndErrorContract() throws Exception {
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/test-validation"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void inputMethodValidationKeeps400AndSafeRequestDetail() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/test-validation/method")
                        .param("count", "-1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("request"))
                .andExpect(jsonPath("$.details[0].message").value("Supply valid request values."));
    }

    @Test
    void invalidControllerReturnValueIsServerFailureBeforeAnyBodyIsWritten() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/test-validation/return")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Request could not be completed."))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertFalse(
                        result.getResponse().getContentAsString().contains("private-return-value")));
    }

    @RestController
    static class ValidationController {
        @PostMapping("/api/test-validation")
        public void validate(@Valid @RequestBody Input input) {}

        @GetMapping("/api/test-validation/method")
        public void method(@RequestParam("count") @Min(value = 1, message = "private-input-value") int count) {}

        @GetMapping("/api/test-validation/return")
        @NotBlank(message = "private-return-value")
        public String invalidReturn() { return ""; }
    }

    record Input(
            @NotBlank(message = "Unsafe validator text") String username,
            @NotBlank(message = "Unsafe validator text") String email) {}
}
