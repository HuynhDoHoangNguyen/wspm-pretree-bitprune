package vn.edu.wspm.backend.controllers.api.v1.admin;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.dto.run.*;
import vn.edu.wspm.config.*;
import vn.edu.wspm.backend.services.run.RunConfigurationValidityGate;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactResult;
import vn.edu.wspm.backend.mappers.RunConfigurationValidationResponseMapper;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.services.run.RunConfigurationValidationService;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.web.wdm.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RunConfigurationValidationControllerTest {
    private static final String PATH = "/api/v1/admin/mining-runs/configuration-validation";
    private static final String BODY = "{\"snapshotId\":\"550e8400-e29b-41d4-a716-446655440000\",\"algorithm\":\"WSPM_PRETREE\",\"minsupMode\":\"PERCENT\",\"minsupValue\":14}";
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final RunConfigurationValidationService service = mock(RunConfigurationValidationService.class);
    private MockMvc mvc;
    @AfterAll static void close() { FACTORY.close(); }
    @BeforeEach void setup() {
        var beans = new StaticListableBeanFactory();
        beans.addBean("authorizer", (WdmAdminAuthorizer) req -> {
            if (req.getHeader("X-Test-Identity") == null) throw new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED);
            if (!req.getHeader("X-Test-Identity").equals("ADMIN")) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        });
        mvc = MockMvcBuilders.standaloneSetup(new RunConfigurationValidationController(new RunRequestReader(FACTORY.getValidator()), service))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter(),
                        new WdmRequestFilter(beans.getBeanProvider(WdmAdminAuthorizer.class), JsonMapper.builder().build())).build();
    }
    @Test void authorizationPrecedesMalformedJsonTransportAndLookup() throws Exception {
        mvc.perform(post(PATH).contentType("text/plain").content("{")) .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header("X-Test-Identity", "CUSTOMER").contentType("application/json").content("{"))
                .andExpect(status().isForbidden()); verifyNoInteractions(service);
    }
    @Test void successUsesEnvelopeMatchingRequestIdAndNoStore() throws Exception {
        when(service.validate(any())).thenReturn(new RunConfigurationValidationResponse("id", "WSPM_PRETREE", "v", "a", "b", null,
                new RunConfigurationValidationResponse.MinimumSupport("PERCENT", 14, 0.14, 0.42), "SUPPORTED"));
        var result = mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.configurationStatus").value("SUPPORTED"))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("X-Content-Type-Options", "nosniff")).andReturn();
        assertTrue(result.getResponse().getContentAsString().contains(result.getResponse().getHeader("X-Request-Id")));
        verify(service).validate(new RunCreateRequest("550e8400-e29b-41d4-a716-446655440000", "WSPM_PRETREE", "PERCENT", 14.0));
    }
    @Test void rejectsInvalidJsonLegacyInputsAndQueryBeforeService() throws Exception {
        for (String body : java.util.List.of("{", BODY + " {}", BODY.replace("}", ",\"minsupValue\":2}"),
                BODY.replace("}", ",\"sequenceCount\":3}"), BODY.replace("}", ",\"minWeight\":1}"))) {
            mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").param("force", "true").contentType("application/json").content(BODY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details[0].field").value("query"));
        verifyNoInteractions(service);
    }
    @Test void methodAndMediaAreRestricted() throws Exception {
        mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").contentType("text/plain").content(BODY)).andExpect(status().isUnsupportedMediaType());
        mvc.perform(get(PATH).header("X-Test-Identity", "ADMIN")).andExpect(status().isMethodNotAllowed()); verifyNoInteractions(service);
    }
    @Test void preservesServiceErrorEnvelopeWithoutRawInput() throws Exception {
        when(service.validate(any())).thenThrow(new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE));
        var result = mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").contentType("application/json").content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_RESOURCE_STATE"))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        assertTrue(result.getResponse().getContentAsString().contains(result.getResponse().getHeader("X-Request-Id")));
    }

    @Test void zeroGateUses422WithSafeDetailsAndMatchingRequestId() throws Exception {
        var resolved = new MinimumSupportResolver().resolve(MinimumSupport.percentage(0), 3);
        var failure = assertThrows(ApplicationException.class, () -> new RunConfigurationValidityGate().requireSupported(resolved));
        when(service.validate(any())).thenThrow(failure);
        var result = mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").contentType("application/json").content(BODY.replace("14}", "0}")))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("UNSUPPORTED_MINING_CONFIGURATION"))
                .andExpect(jsonPath("$.details[0].field").value("minsupValue"))
                .andExpect(jsonPath("$.details[0].code").value("ZERO_EFFECTIVE_THRESHOLD"))
                .andExpect(jsonPath("$.details[0].message").value("Effective minimum support must be positive while R6 remains open."))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        assertTrue(result.getResponse().getContentAsString().contains(result.getResponse().getHeader("X-Request-Id")));
    }
    @Test void resourceErrorsRetainOriginalClassificationAndInternalFailureIsNot422() throws Exception {
        for (var code : java.util.List.of(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.INVALID_RESOURCE_STATE, ErrorCode.SERVICE_UNAVAILABLE)) {
            doThrow(new ApplicationException(code)).when(service).validate(any());
            mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").contentType("application/json").content(BODY))
                    .andExpect(status().is(code.status().value())).andExpect(jsonPath("$.code").value(code.name()));
        }
        doThrow(new IllegalArgumentException("private-internal-resolution")).when(service).validate(any());
        var result = mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").contentType("application/json").content(BODY))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("private-internal-resolution"));
    }
    @Test void responseMapperHasClosedSchemaAndNoPrivateReferencesOrRunAuthorization() {
        var publication = new ConversionArtifactResult("snapshot", "private-attempt", "private-validation",
                new ConversionArtifactResult.ArtifactFile("private-path", "a".repeat(64), 10),
                new ConversionArtifactResult.ArtifactFile("private-path", "b".repeat(64), 20),
                new ConversionArtifactResult.Statistics(3, 1, 2, 6, 3, 8, 2, 3, 8.0 / 3));
        for (var config : java.util.List.of(MinimumSupport.percentage(14), MinimumSupport.absolute(0.25))) {
            var resolved = new MinimumSupportResolver().resolve(config, 3);
            var dto = new RunConfigurationValidationResponseMapper().map(publication, "WSPM_PRETREE", resolved);
            var node = JsonMapper.builder().build().valueToTree(dto);
            assertEquals(java.util.Set.of("snapshotId", "algorithm", "artifactVersion", "sequenceSha256", "mappingSha256", "sequenceCount", "minimumSupport", "configurationStatus"), java.util.Set.copyOf(node.propertyNames()));
            assertEquals("3", node.get("sequenceCount").asString()); assertTrue(node.get("sequenceCount").isString());
            assertEquals(java.util.Set.of("mode", "configuredValue", "relativeFraction", "effectiveThreshold"), java.util.Set.copyOf(node.get("minimumSupport").propertyNames()));
            assertEquals(config.mode() == MinimumSupportMode.ABSOLUTE, node.at("/minimumSupport/relativeFraction").isNull());
            assertEquals(resolved.effectiveThreshold(), node.at("/minimumSupport/effectiveThreshold").asDouble());
            assertEquals("SUPPORTED", node.get("configurationStatus").asString());
            assertFalse(node.toString().contains("private-")); assertFalse(node.has("runId")); assertFalse(node.has("executionEligibility"));
        }
    }
}
