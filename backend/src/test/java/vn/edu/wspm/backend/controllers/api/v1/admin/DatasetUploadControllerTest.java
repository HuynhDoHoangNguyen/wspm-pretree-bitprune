package vn.edu.wspm.backend.controllers.api.v1.admin;

import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.exception.GlobalExceptionHandler;
import vn.edu.wspm.backend.services.dataset.CsvUploadService;
import vn.edu.wspm.backend.services.dataset.DatasetSnapshotService;
import vn.edu.wspm.backend.dto.dataset.DatasetSnapshotResponse;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.web.wdm.WdmAdminAuthorizer;
import vn.edu.wspm.backend.web.wdm.WdmRequestFilter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MVC + actual transport service; permissive test authorizer is not shared Auth evidence. */
class DatasetUploadControllerTest {
    static final String PATH = "/api/v1/admin/dataset-snapshots";
    MockMvc mvc;

    @BeforeEach
    void setup() {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("testAdmin", (WdmAdminAuthorizer) request -> {});
        var snapshots = mock(DatasetSnapshotService.class);
        when(snapshots.acquire(any())).thenReturn(new DatasetSnapshotResponse(
                "fixture-id", "CSV", "fixture.csv", "PENDING", "NOT_STARTED", "NOT_COMPUTED",
                "0".repeat(64), "1", null, null, null, Instant.parse("2026-11-05T00:00:00Z")));
        mvc = MockMvcBuilders.standaloneSetup(new DatasetUploadController(
                        new CsvUploadService(new MultipartConfigElement("", 4096, 8192, 0), snapshots)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter(), new WdmRequestFilter(
                        beans.getBeanProvider(WdmAdminAuthorizer.class), JsonMapper.builder().build()))
                .build();
    }

    @Test
    void contractFixturesReachStorageBoundaryWithoutContentValidation() throws Exception {
        for (String name : new String[]{"valid.csv", "empty.csv", "business-invalid.csv"}) {
            byte[] bytes = new ClassPathResource("wdm/csv-v1/" + name).getContentAsByteArray();
            mvc.perform(multipart(PATH).file(new MockMultipartFile("file", name, "text/csv", bytes)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.validationStatus").value("PENDING"));
        }
    }

    @Test
    void supportedMimeVariantsAndUppercaseSuffixReachStorageBoundary() throws Exception {
        for (String mime : new String[]{null, "text/csv", "TEXT/CSV; charset=UTF-8",
                "application/csv", "application/vnd.ms-excel", "application/octet-stream"}) {
            mvc.perform(multipart(PATH).file(new MockMultipartFile("file", "orders.CSV", mime,
                            new byte[]{(byte) 0xff})))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void rejectsMissingEmptyDuplicateExtraAndTextFields() throws Exception {
        mvc.perform(multipart(PATH)).andExpect(status().isBadRequest());
        mvc.perform(multipart(PATH).file(file("file", "a.csv", "text/csv", 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(PATH).file(file("file", "a.csv", "text/csv", 1))
                        .file(file("file", "b.csv", "text/csv", 1)))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(PATH).file(file("file", "a.csv", "text/csv", 1))
                        .file(file("other", "b.csv", "text/csv", 1)))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(PATH).file(file("file", "a.csv", "text/csv", 1))
                        .param("source_type", "DATABASE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnsupportedTypesAndSuffixes() throws Exception {
        for (String mime : new String[]{"text/plain", "image/png", "text/*", "invalid"}) {
            mvc.perform(multipart(PATH).file(file("file", "a.csv", mime, 1)))
                    .andExpect(status().isUnsupportedMediaType());
        }
        mvc.perform(multipart(PATH).file(file("file", "a.csv.exe", "text/csv", 1)))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post(PATH).contentType("application/json").content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void rejectsClientPathsAndUnsafeNamesWithoutEchoingThem() throws Exception {
        for (String name : new String[]{"../private.csv", "C:\\private.csv", "/private.csv",
                "bad\r\n.csv", "a".repeat(256) + ".csv", ""}) {
            var result = mvc.perform(multipart(PATH).file(file("file", name, "text/csv", 1)))
                    .andExpect(status().isBadRequest()).andReturn();
            assertFalse(result.getResponse().getContentAsString().contains("private.csv"));
        }
    }

    @Test
    void exactByteLimitPassesAndOneMoreByteFailsWithSafeMetadata() throws Exception {
        mvc.perform(multipart(PATH).file(file("file", "a.csv", "text/csv", 4096)))
                .andExpect(status().isCreated());
        var response = mvc.perform(multipart(PATH).file(file("file", "a.csv", "text/csv", 4097))
                        .header("X-Request-Id", "client-marker"))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.path").value(PATH))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        var body = JsonMapper.builder().build().readTree(response.getContentAsString());
        assertEquals(response.getHeader("X-Request-Id"), body.get("requestId").asString());
        assertNotEquals("client-marker", response.getHeader("X-Request-Id"));
        assertFalse(body.toString().contains("snapshotId"));
    }

    @Test
    void rejectsUnboundedOrInconsistentConfiguration() {
        for (long[] sizes : new long[][]{{0, 2}, {-1, 2}, {1, -1}, {2, 2}, {3, 2}}) {
            assertThrows(IllegalArgumentException.class, () -> new CsvUploadService(
                    new MultipartConfigElement("", sizes[0], sizes[1], 0), mock(DatasetSnapshotService.class)));
        }
    }

    private static MockMultipartFile file(String part, String name, String mime, int bytes) {
        return new MockMultipartFile(part, name, mime, new byte[bytes]);
    }
}
