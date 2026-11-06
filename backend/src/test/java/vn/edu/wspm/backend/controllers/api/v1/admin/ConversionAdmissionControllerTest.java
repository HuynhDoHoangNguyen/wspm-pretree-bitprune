package vn.edu.wspm.backend.controllers.api.v1.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.services.dataset.conversion.*;
import vn.edu.wspm.backend.dto.dataset.ConversionResponse;
import vn.edu.wspm.backend.mappers.ConversionResponseMapper;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.web.wdm.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MVC contract with a test authorizer; real shared Auth is covered by browser integration. */
class ConversionAdmissionControllerTest {
    private static final String ID = "00000000-0000-0000-0000-000000000001";
    private static final String PATH = "/api/v1/admin/dataset-snapshots/" + ID + "/conversion";
    private final SnapshotConversionService conversion = mock(SnapshotConversionService.class);
    private final ConversionReadService reads = mock(ConversionReadService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        var factory = new StaticListableBeanFactory();
        factory.addBean("authorizer", (WdmAdminAuthorizer) request -> {
            String identity = request.getHeader("X-Test-Identity");
            if (identity == null) throw new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED);
            if (!identity.equals("ADMIN")) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        });
        mvc = MockMvcBuilders.standaloneSetup(new ConversionAdmissionController(conversion, reads))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter(), new WdmRequestFilter(factory.getBeanProvider(WdmAdminAuthorizer.class),
                        JsonMapper.builder().build())).build();
    }

    @Test void authorizationPrecedesInputAndGate() throws Exception {
        mvc.perform(post(PATH.replace(ID, "bad")).content("private-input")).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH.replace(ID, "bad")).header("X-Test-Identity", "CUSTOMER").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(conversion, reads);
    }

    @Test void rejectsBodyAndQueryBeforeGate() throws Exception {
        mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("request"))
                .andExpect(jsonPath("$.details[0].code").value("INVALID_VALUE"))
                .andExpect(jsonPath("$.details[0].message").value("Conversion admission accepts no body or query parameters."));
        mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN").param("force", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("request"))
                .andExpect(jsonPath("$.details[0].code").value("INVALID_VALUE"))
                .andExpect(jsonPath("$.details[0].message").value("Conversion admission accepts no body or query parameters."));
        verifyNoInteractions(conversion, reads);
    }

    @Test void ineligibleSnapshotIsConflict() throws Exception {
        when(conversion.convert(ID)).thenThrow(new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE));
        mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESOURCE_STATE"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void completedPostUsesExecutionThenSafeVerifiedReadProjection() throws Exception {
        var response = new ConversionResponse(ID, "SUCCEEDED", "COMPUTED", null, null, null, null,
                ConversionArtifactCodec.VERSION, null, null, null);
        var bundle = mock(ConversionReadService.VerifiedBundle.class);
        when(bundle.response()).thenReturn(response); when(reads.verified(ID)).thenReturn(bundle);
        mvc.perform(post(PATH).header("X-Test-Identity", "ADMIN")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.snapshotId").value(ID))
                .andExpect(jsonPath("$.data.conversionStatus").value("SUCCEEDED"))
                .andExpect(header().string("Cache-Control", "no-store"));
        var ordered = inOrder(conversion, reads); ordered.verify(conversion).convert(ID); ordered.verify(reads).verified(ID);
    }

    @Test void allReadRoutesAuthorizeBeforeBadInputAndNeverExecuteConversion() throws Exception {
        for (String path : java.util.List.of(PATH, PATH.replace("/conversion", "/artifacts/sequence"), PATH.replace("/conversion", "/artifacts/mapping"))) {
            mvc.perform(get(path.replace(ID, "bad")).param("force", "true")).andExpect(status().isUnauthorized());
            mvc.perform(get(path.replace(ID, "bad")).header("X-Test-Identity", "CUSTOMER")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(conversion, reads);
    }

    @Test void readsRejectBodyAndQueryBeforeResourceAccess() throws Exception {
        for (String path : java.util.List.of(PATH, PATH.replace("/conversion", "/artifacts/sequence"), PATH.replace("/conversion", "/artifacts/mapping"))) {
            mvc.perform(get(path).header("X-Test-Identity", "ADMIN").param("path", "private")).andExpect(status().isBadRequest());
            mvc.perform(get(path).header("X-Test-Identity", "ADMIN").content(" ")).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(conversion, reads);
    }

    @Test void statusGetReturnsSafeEnvelopeWithoutCallingExecution() throws Exception {
        when(reads.status(ID)).thenReturn(new ConversionResponseMapper().pending(ID, "NOT_STARTED", null, null, null));
        mvc.perform(get(PATH).header("X-Test-Identity", "ADMIN")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversionStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.statistics").isEmpty()).andExpect(jsonPath("$.data.sequence").isEmpty())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        verifyNoInteractions(conversion);
    }

    @Test void downloadsReturnOnlyVerifiedBytesAndFixedAttachmentHeaders() throws Exception {
        var bundle = mock(ConversionReadService.VerifiedBundle.class);
        byte[] sequence = "1 -1 -2\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), mapping = "{}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(reads.verified(ID)).thenReturn(bundle); when(bundle.sequenceBytes()).thenReturn(sequence); when(bundle.mappingBytes()).thenReturn(mapping);
        mvc.perform(get(PATH.replace("/conversion", "/artifacts/sequence")).header("X-Test-Identity", "ADMIN"))
                .andExpect(status().isOk()).andExpect(content().bytes(sequence))
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andExpect(header().string("Content-Length", Integer.toString(sequence.length)))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"snapshot-" + ID + ".spmf\""))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(PATH.replace("/conversion", "/artifacts/mapping")).header("X-Test-Identity", "ADMIN"))
                .andExpect(status().isOk()).andExpect(content().bytes(mapping))
                .andExpect(content().contentType("application/json"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        verifyNoInteractions(conversion);
    }

    @Test void unavailableDownloadRemainsJsonErrorWithNoPartialAttachment() throws Exception {
        when(reads.verified(ID)).thenThrow(new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE));
        mvc.perform(get(PATH.replace("/conversion", "/artifacts/sequence")).header("X-Test-Identity", "ADMIN"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(header().doesNotExist("Content-Disposition"));
        verifyNoInteractions(conversion);
    }
}
