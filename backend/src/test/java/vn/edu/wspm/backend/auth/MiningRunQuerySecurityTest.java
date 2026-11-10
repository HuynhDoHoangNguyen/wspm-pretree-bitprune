package vn.edu.wspm.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.controllers.api.v1.admin.MiningPreviewController;
import vn.edu.wspm.backend.controllers.api.v1.admin.RunConfigurationValidationController;
import vn.edu.wspm.backend.controllers.api.v1.admin.miningrun.MiningRunQueryController;
import vn.edu.wspm.backend.dto.auth.IdentityResponse;
import vn.edu.wspm.backend.dto.miningrun.MiningRunPageResponse;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.exception.GlobalExceptionHandler;
import vn.edu.wspm.backend.services.miningrun.MiningRunQueryService;
import vn.edu.wspm.backend.services.run.MiningPreviewService;
import vn.edu.wspm.backend.services.run.RunConfigurationValidationService;
import vn.edu.wspm.backend.web.auth.ApiSecurityFilter;
import vn.edu.wspm.backend.web.auth.SharedRequestAuthenticator;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.web.wdm.*;

/** Real controllers/filters with mocked identity and services; no DB, mining or Boot startup. */
class MiningRunQuerySecurityTest {
    private final SharedRequestAuthenticator auth = mock(SharedRequestAuthenticator.class);
    private final MiningRunQueryService query = mock(MiningRunQueryService.class);
    private final RunRequestReader reader = mock(RunRequestReader.class);
    private final RunConfigurationValidationService validation = mock(RunConfigurationValidationService.class);
    private final MiningPreviewService preview = mock(MiningPreviewService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        var mapper = JsonMapper.builder().build();
        ObjectProvider<WdmAdminAuthorizer> provider = mock(ObjectProvider.class);
        when(provider.stream()).thenAnswer(call -> Stream.of(new SharedAuthWdmAdminAuthorizer(auth)));
        when(query.findAll(0, 20, null, null)).thenReturn(new MiningRunPageResponse(List.of(), 0, 20, 0, 0));
        mvc = MockMvcBuilders.standaloneSetup(new MiningRunQueryController(query),
                        new RunConfigurationValidationController(reader, validation),
                        new MiningPreviewController(reader, preview))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter(), new ApiSecurityFilter(mapper, auth), new WdmRequestFilter(provider, mapper))
                .build();
    }

    private void identity(String role) {
        when(auth.authenticate(any())).thenReturn(new IdentityResponse("admin", "unit", null,
                "Unit", "ACTIVE", List.of(role), false));
    }

    private MockHttpServletRequestBuilder request(String suffix) {
        String path = "/api/v1/admin/mining-runs" + suffix;
        return suffix.equals("/preview") || suffix.equals("/configuration-validation")
                ? post(path).contentType(MediaType.APPLICATION_JSON).content("{}") : get(path);
    }

    @ParameterizedTest @ValueSource(strings = {"", "/run-id", "/preview", "/configuration-validation"})
    void guestCannotReachReadOrExistingPostServices(String suffix) throws Exception {
        when(auth.authenticate(any())).thenThrow(new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED));
        mvc.perform(request(suffix)).andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(reader, validation, preview);
        verify(query, never()).findAll(anyInt(), anyInt(), any(), any());
        verify(query, never()).findById(any());
    }

    @ParameterizedTest @ValueSource(strings = {"", "/run-id", "/preview", "/configuration-validation"})
    void customerCannotReachReadOrExistingPostServices(String suffix) throws Exception {
        identity("CUSTOMER");
        mvc.perform(request(suffix)).andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(reader, validation, preview);
        verify(query, never()).findAll(anyInt(), anyInt(), any(), any());
        verify(query, never()).findById(any());
    }

    @ParameterizedTest @ValueSource(strings = {"", "/run-id", "/preview", "/configuration-validation"})
    void adminReachesReadsAndExistingPostsWithNoStore(String suffix) throws Exception {
        identity("ADMIN");
        mvc.perform(request(suffix)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("SUCCESS"));
        switch (suffix) {
            case "" -> verify(query).findAll(0, 20, null, null);
            case "/run-id" -> verify(query).findById("run-id");
            case "/preview" -> verify(preview).preview(any());
            case "/configuration-validation" -> verify(validation).validate(any());
            default -> throw new AssertionError("Unexpected test path");
        }
    }

    @Test void unrelatedApiRemainsDefaultDenied() throws Exception {
        identity("ADMIN");
        var response = mvc.perform(get("/api/v1/private")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED")).andReturn().getResponse();
        assertEquals("application/json", response.getContentType());
    }
}
