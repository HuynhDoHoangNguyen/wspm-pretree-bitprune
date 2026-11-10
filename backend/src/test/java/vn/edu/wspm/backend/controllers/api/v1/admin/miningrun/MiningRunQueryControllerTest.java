package vn.edu.wspm.backend.controllers.api.v1.admin.miningrun;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import vn.edu.wspm.backend.dto.auth.IdentityResponse;
import vn.edu.wspm.backend.dto.miningrun.MiningRunDetailResponse;
import vn.edu.wspm.backend.dto.miningrun.MiningRunPageResponse;
import vn.edu.wspm.backend.dto.miningrun.MiningRunSummaryResponse;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.exception.GlobalExceptionHandler;
import vn.edu.wspm.backend.services.miningrun.MiningRunQueryService;
import vn.edu.wspm.backend.services.miningrun.MiningRunQueryService.InvalidMiningRunFilterException;
import vn.edu.wspm.backend.services.miningrun.MiningRunQueryService.MiningRunNotFoundException;
import vn.edu.wspm.backend.web.auth.ApiSecurityFilter;
import vn.edu.wspm.backend.web.auth.SharedRequestAuthenticator;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.web.wdm.SharedAuthWdmAdminAuthorizer;
import vn.edu.wspm.backend.web.wdm.WdmAdminAuthorizer;
import vn.edu.wspm.backend.web.wdm.WdmRequestFilter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/** Standalone MVC + shared filters/advice; session verification is mocked. No Boot/DB startup. */
@ExtendWith(MockitoExtension.class)
class MiningRunQueryControllerTest {
    @Mock private MiningRunQueryService miningRunQueryService;
    @Mock private SharedRequestAuthenticator authenticator;
    @Mock private ObjectProvider<WdmAdminAuthorizer> authorizers;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        given(authenticator.authenticate(any())).willReturn(identity("ADMIN"));
        var admin = new SharedAuthWdmAdminAuthorizer(authenticator);
        given(authorizers.stream()).willAnswer(call -> Stream.of(admin));
        var mapper = JsonMapper.builder().build();
        mockMvc = MockMvcBuilders.standaloneSetup(new MiningRunQueryController(miningRunQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter(), new ApiSecurityFilter(mapper, authenticator),
                        new WdmRequestFilter(authorizers, mapper))
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/admin/mining-run-history returns paginated mining runs")
    void shouldReturnPaginatedMiningRuns() throws Exception {
        MiningRunSummaryResponse item = new MiningRunSummaryResponse(
                "run-101",
                "WSPM_BITPRUNE",
                "SUCCEEDED",
                "SIGN",
                "snap-101",
                "RELATIVE",
                0.01,
                15.0,
                120L,
                450L,
                1500L,
                45000000L,
                Instant.parse("2026-11-03T08:00:00Z"),
                Instant.parse("2026-11-03T08:00:01Z"),
                Instant.parse("2026-11-03T08:00:02Z")
        );

        MiningRunPageResponse pageResponse = new MiningRunPageResponse(
                List.of(item),
                0,
                20,
                1L,
                1
        );

        given(miningRunQueryService.findAll(0, 20, "WSPM_BITPRUNE", "SUCCEEDED"))
                .willReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/mining-run-history")
                        .param("page", "0")
                        .param("size", "20")
                        .param("algorithm", "WSPM_BITPRUNE")
                        .param("status", "SUCCEEDED"))
                .andExpect(status().isOk())
                .andExpect(successEnvelope())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items[0].runId").value("run-101"))
                .andExpect(jsonPath("$.data.items[0].algorithm").value("WSPM_BITPRUNE"))
                .andExpect(jsonPath("$.data.items[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.items[0].peakSampledJvmHeapBytes").value(45000000L))
                .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-11-03T08:00:00Z"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/mining-run-history/{runId} returns detail response")
    void shouldReturnMiningRunDetail() throws Exception {
        MiningRunDetailResponse detail = new MiningRunDetailResponse(
                "run-102",
                "WSPM_PRETREE",
                "SUCCEEDED",
                "PUMSB",
                "snap-102",
                "ABSOLUTE",
                25.0,
                25.0,
                300L,
                1200L,
                3200L,
                68000000L,
                Instant.parse("2026-11-03T09:00:00Z"),
                Instant.parse("2026-11-03T09:00:01Z"),
                Instant.parse("2026-11-03T09:00:04Z"),
                0.0005,
                0.0134,
                0.0000002,
                0.00025,
                500L,
                200L,
                2100L,
                2300L,
                400L,
                null,
                49046L,
                2113L,
                "VALID"
        );

        given(miningRunQueryService.findById("run-102")).willReturn(detail);

        mockMvc.perform(get("/api/v1/admin/mining-run-history/run-102"))
                .andExpect(status().isOk())
                .andExpect(successEnvelope())
                .andExpect(jsonPath("$.data.runId").value("run-102"))
                .andExpect(jsonPath("$.data.algorithm").value("WSPM_PRETREE"))
                .andExpect(jsonPath("$.data.sequenceCount").value(49046))
                .andExpect(jsonPath("$.data.distinctItemCount").value(2113))
                .andExpect(jsonPath("$.data.validationStatus").value("VALID"))
                .andExpect(jsonPath("$.data.errorMessage").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/admin/mining-run-history/{runId} returns 404 when not found")
    void shouldReturn404WhenNotFound() throws Exception {
        given(miningRunQueryService.findById("run-999"))
                .willThrow(new MiningRunNotFoundException());

        mockMvc.perform(get("/api/v1/admin/mining-run-history/run-999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldBindDefaultsAndReturnEmptyPaginationEnvelope() throws Exception {
        given(miningRunQueryService.findAll(0, 20, null, null))
                .willReturn(new MiningRunPageResponse(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/v1/admin/mining-run-history"))
                .andExpect(status().isOk())
                .andExpect(successEnvelope())
                .andExpect(jsonPath("$.data.keys()", containsInAnyOrder(
                        "items", "page", "size", "totalElements", "totalPages")))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0));
        verify(miningRunQueryService).findAll(0, 20, null, null);
    }

    @Test
    void shouldBindNonDefaultPageSizeAndCombinedFilters() throws Exception {
        given(miningRunQueryService.findAll(2, 50, "WSPM_PRETREE", "FAILED"))
                .willReturn(new MiningRunPageResponse(List.of(), 2, 50, 0L, 0));

        mockMvc.perform(get("/api/v1/admin/mining-run-history")
                        .param("page", "2").param("size", "50")
                        .param("algorithm", "WSPM_PRETREE").param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(successEnvelope())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(50));
        verify(miningRunQueryService).findAll(2, 50, "WSPM_PRETREE", "FAILED");
    }

    @Test
    void shouldRejectMalformedAndOverflowingNumericParametersBeforeServiceCall() throws Exception {
        for (String parameter : List.of("page", "size")) {
            for (String invalid : List.of("abc", "1.5", "2147483648")) {
                mockMvc.perform(get("/api/v1/admin/mining-run-history").param(parameter, invalid))
                        .andExpect(status().isBadRequest());
            }
        }
        verifyNoInteractions(miningRunQueryService);
    }

    @Test
    void shouldSerializeNullDetailValuesAndOnlyApprovedFields() throws Exception {
        MiningRunDetailResponse detail = new MiningRunDetailResponse(
                "run-null", "WSPM_PRETREE", "QUEUED", "BENCHMARK", "snap-null",
                "ABSOLUTE", 10.0, 10.0, null, null, null, null,
                Instant.parse("2026-11-03T08:00:00Z"), null, null,
                null, null, null, 0.2, null, null, null, null, null,
                null, null, null, null);
        given(miningRunQueryService.findById("run-null")).willReturn(detail);

        var response = mockMvc.perform(get("/api/v1/admin/mining-run-history/run-null"))
                .andExpect(status().isOk())
                .andExpect(successEnvelope())
                .andExpect(jsonPath("$.data.keys()", containsInAnyOrder(
                        "runId", "algorithm", "status", "datasetSource", "snapshotId", "minsupMode",
                        "minsupConfigured", "effectiveThreshold", "patternCount", "candidateCount",
                        "totalExecutionMs", "peakSampledJvmHeapBytes", "createdAt", "startedAt", "finishedAt",
                        "minsupRelativeFraction", "maxW", "minW", "meanW", "datasetIoMs", "preprocessingMs",
                        "dfsMiningMs", "completeAlgorithmMs", "outputIoMs", "errorMessage", "sequenceCount",
                        "distinctItemCount", "validationStatus")))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-03T08:00:00Z"))
                .andExpect(jsonPath("$.data.legacyMetadataJson").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.legacy_metadata_json").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.runTime").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.inputSnapshot").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.executionTime").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.memoryUsage").doesNotHaveJsonPath());
        for (String field : List.of("patternCount", "candidateCount", "totalExecutionMs",
                "peakSampledJvmHeapBytes", "startedAt", "finishedAt", "minsupRelativeFraction",
                "maxW", "minW", "datasetIoMs", "preprocessingMs", "dfsMiningMs", "completeAlgorithmMs",
                "outputIoMs", "errorMessage", "sequenceCount", "distinctItemCount", "validationStatus")) {
            response.andExpect(jsonPath("$.data", hasKey(field)))
                    .andExpect(jsonPath("$.data." + field, nullValue()));
        }
    }

    @Test
    void shouldSerializeOnlyApprovedSummaryFieldsAndPreserveZero() throws Exception {
        MiningRunSummaryResponse item = new MiningRunSummaryResponse(
                "run-zero", "WSPM_BITPRUNE", "SUCCEEDED", "BENCHMARK", "snap-zero",
                "ABSOLUTE", 10.0, 10.0, 0L, 0L, 0L, 0L,
                Instant.parse("2026-11-03T08:00:00Z"), null, null);
        given(miningRunQueryService.findAll(0, 20, null, null))
                .willReturn(new MiningRunPageResponse(List.of(item), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/v1/admin/mining-run-history"))
                .andExpect(status().isOk())
                .andExpect(successEnvelope())
                .andExpect(jsonPath("$.data.items[0].keys()", containsInAnyOrder(
                        "runId", "algorithm", "status", "datasetSource", "snapshotId", "minsupMode",
                        "minsupConfigured", "effectiveThreshold", "patternCount", "candidateCount",
                        "totalExecutionMs", "peakSampledJvmHeapBytes", "createdAt", "startedAt", "finishedAt")))
                .andExpect(jsonPath("$.data.items[0].patternCount").value(0))
                .andExpect(jsonPath("$.data.items[0].candidateCount").value(0))
                .andExpect(jsonPath("$.data.items[0].totalExecutionMs").value(0))
                .andExpect(jsonPath("$.data.items[0].peakSampledJvmHeapBytes").value(0))
                .andExpect(jsonPath("$.data.items[0]", hasKey("startedAt")))
                .andExpect(jsonPath("$.data.items[0].startedAt", nullValue()));
    }

    @Test
    void shouldReturnSharedValidationContractWithoutRejectedInput() throws Exception {
        for (String field : List.of("algorithm", "status")) {
            String rejected = "unsupported-synthetic-private-input";
            given(miningRunQueryService.findAll(0, 20,
                    field.equals("algorithm") ? rejected : null, field.equals("status") ? rejected : null))
                    .willThrow(new InvalidMiningRunFilterException(field));
            mockMvc.perform(get("/api/v1/admin/mining-run-history").param(field, rejected))
                    .andExpect(errorBody(400, "VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.details[0].field").value(field))
                    .andExpect(jsonPath("$.details[0].code").value("INVALID_FILTER"))
                    .andExpect(content().string(not(containsString(rejected))));
        }
    }

    @Test
    void shouldUseSharedMalformedRequestContractForNumericOverflow() throws Exception {
        for (String field : List.of("page", "size")) {
            for (String value : List.of("invalid-private-input", "1.5", "2147483648", "-2147483649")) {
                mockMvc.perform(get("/api/v1/admin/mining-run-history").param(field, value))
                        .andExpect(errorBody(400, "MALFORMED_REQUEST"))
                        .andExpect(jsonPath("$.details").isEmpty())
                        .andExpect(content().string(not(containsString(value))));
            }
        }
        verifyNoInteractions(miningRunQueryService);
    }

    @Test
    void shouldReturnSharedNotFoundContract() throws Exception {
        given(miningRunQueryService.findById("missing-run")).willThrow(new MiningRunNotFoundException());
        mockMvc.perform(get("/api/v1/admin/mining-run-history/missing-run"))
                .andExpect(errorBody(404, "RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void shouldRedactUnexpectedAndWrappedFailuresAndPreserveServiceUnavailable() throws Exception {
        String privateText = "synthetic internal diagnostic";
        for (RuntimeException failure : List.of(new IllegalStateException(privateText),
                new IllegalStateException(privateText, new InvalidMiningRunFilterException("algorithm")),
                new DataAccessResourceFailureException(privateText))) {
            doThrow(failure).when(miningRunQueryService).findAll(0, 20, null, null);
            boolean unavailable = failure instanceof DataAccessResourceFailureException;
            mockMvc.perform(get("/api/v1/admin/mining-run-history"))
                    .andExpect(errorBody(unavailable ? 503 : 500, unavailable ? "SERVICE_UNAVAILABLE" : "INTERNAL_ERROR"))
                    .andExpect(content().string(not(containsString(privateText))))
                    .andExpect(jsonPath("$.stackTrace").doesNotHaveJsonPath());
        }
    }

    @Test
    void shouldRejectAnonymousRequestsBeforeValidationOrLookup() throws Exception {
        given(authenticator.authenticate(any())).willThrow(new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED));
        mockMvc.perform(get("/api/v1/admin/mining-run-history").param("page", "invalid"))
                .andExpect(errorBody(401, "AUTHENTICATION_REQUIRED"))
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        mockMvc.perform(get("/api/v1/admin/mining-run-history/private-run"))
                .andExpect(errorBody(401, "AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(miningRunQueryService);
    }

    @Test
    void shouldRejectCustomerIdentityThroughSharedAdminAuthorizer() throws Exception {
        given(authenticator.authenticate(any())).willReturn(identity("CUSTOMER"));
        mockMvc.perform(get("/api/v1/admin/mining-run-history").param("page", "invalid"))
                .andExpect(errorBody(403, "ACCESS_DENIED"));
        mockMvc.perform(get("/api/v1/admin/mining-run-history/private-run"))
                .andExpect(errorBody(403, "ACCESS_DENIED"));
        verifyNoInteractions(miningRunQueryService);
    }

    @Test
    void shouldCorrelateSuccessWithoutTrustingClientRequestId() throws Exception {
        given(miningRunQueryService.findAll(0, 20, null, null))
                .willReturn(new MiningRunPageResponse(List.of(), 0, 20, 0L, 0));
        var result = mockMvc.perform(get("/api/v1/admin/mining-run-history").header("X-Request-Id", "untrusted-id"))
                .andExpect(status().isOk()).andExpect(successEnvelope()).andReturn();
        assertNotEquals("untrusted-id", result.getResponse().getHeader("X-Request-Id"));
    }

    private ResultMatcher successEnvelope() {
        return result -> {
            jsonPath("$.keys()", containsInAnyOrder("code", "message", "data", "requestId")).match(result);
            jsonPath("$.code").value("SUCCESS").match(result);
            jsonPath("$.message").value("Request completed successfully.").match(result);
            jsonPath("$.requestId").value(result.getResponse().getHeader("X-Request-Id")).match(result);
            assertTrue(result.getResponse().getHeader("X-Request-Id").matches("[0-9a-f-]{36}"));
            header().string("Cache-Control", "no-store").match(result);
            jsonPath("$.data.code").doesNotHaveJsonPath().match(result);
        };
    }

    private ResultMatcher errorBody(int httpStatus, String code) {
        return result -> {
            status().is(httpStatus).match(result);
            content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON).match(result);
            jsonPath("$.keys()", containsInAnyOrder("status", "code", "message", "path", "requestId", "details")).match(result);
            jsonPath("$.status").value(httpStatus).match(result);
            jsonPath("$.code").value(code).match(result);
            jsonPath("$.message").isNotEmpty().match(result);
            jsonPath("$.path").value(result.getRequest().getRequestURI()).match(result);
            jsonPath("$.requestId").value(result.getResponse().getHeader("X-Request-Id")).match(result);
            jsonPath("$.details").isArray().match(result);
            header().string("Cache-Control", "no-store").match(result);
        };
    }

    private static IdentityResponse identity(String role) {
        return new IdentityResponse("test-account", "test-user", "test@example.invalid", "Test", "ACTIVE", List.of(role), false);
    }
}
