package vn.edu.wspm.backend.controllers.api.v1.admin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.exception.GlobalExceptionHandler;
import vn.edu.wspm.backend.services.dataset.CsvUploadService;
import vn.edu.wspm.backend.services.dataset.DatasetSnapshotService;
import vn.edu.wspm.backend.dto.dataset.DatasetSnapshotResponse;
import java.time.Instant;
import static org.mockito.Mockito.*;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.web.wdm.WdmAdminAuthorizer;
import vn.edu.wspm.backend.web.wdm.WdmRequestFilter;

import java.net.URI;
import java.net.http.*;

import static org.junit.jupiter.api.Assertions.*;

/** Real servlet multipart parsing/limits; isolated test authorizer, no DB or shared Auth. */
@SpringBootTest(classes = DatasetUploadHttpTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.servlet.multipart.max-file-size=1024B",
            "spring.servlet.multipart.max-request-size=2048B",
            "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
        })
class DatasetUploadHttpTest {
    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration
    @Import({DatasetUploadController.class, CsvUploadService.class, GlobalExceptionHandler.class,
            RequestIdFilter.class, WdmRequestFilter.class})
    static class Application {
        @Bean
        DatasetSnapshotService testAcquisition() {
            var service = mock(DatasetSnapshotService.class);
            when(service.acquire(any())).thenReturn(new DatasetSnapshotResponse(
                    "fixture-id", "CSV", "orders.csv", "PENDING", "NOT_STARTED", "NOT_COMPUTED",
                    "0".repeat(64), "1024", null, null, null, Instant.parse("2026-11-05T00:00:00Z")));
            return service;
        }

        @Bean
        WdmAdminAuthorizer testAuthorizer() {
            return request -> {
                String role = request.getHeader("X-Test-Identity");
                if (role == null) throw new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED);
                if (!role.equals("ADMIN")) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
            };
        }
    }

    @LocalServerPort int port;

    @Test
    void realParserEnforcesFileAndTotalRequestBytes() throws Exception {
        check(send(body(1024), "ADMIN", "multipart/form-data; boundary=wdm"), 201, "SUCCESS");
        check(send(body(1025), "ADMIN", "multipart/form-data; boundary=wdm"), 413, "PAYLOAD_TOO_LARGE");
        // Small file, but large field/overhead makes the entire request too large.
        String overhead = body(1).replace("--wdm--", "--wdm\r\nContent-Disposition: form-data; name=\"extra\"\r\n\r\n"
                + "x".repeat(2048) + "\r\n--wdm--");
        check(send(overhead, "ADMIN", "multipart/form-data; boundary=wdm"), 413, "PAYLOAD_TOO_LARGE");
    }

    @Test
    void authorizationPrecedesMalformedAndOversizedMultipartParsing() throws Exception {
        for (String identity : new String[]{null, "CUSTOMER"}) {
            int status = identity == null ? 401 : 403;
            String code = identity == null ? "AUTHENTICATION_REQUIRED" : "ACCESS_DENIED";
            check(send(body(4096), identity, "multipart/form-data; boundary=wdm"), status, code);
            check(send("bad", identity, "multipart/form-data"), status, code);
        }
    }

    @Test
    void malformedMultipartGetsSafe400AndUnknownPartGets400() throws Exception {
        check(send("bad", "ADMIN", "multipart/form-data"), 400, "MALFORMED_REQUEST");
        check(send(body(1).replace("name=\"file\"", "name=\"other\""), "ADMIN",
                "multipart/form-data; boundary=wdm"), 400, "VALIDATION_FAILED");
    }

    private static String body(int bytes) {
        return "--wdm\r\nContent-Disposition: form-data; name=\"file\"; filename=\"orders.csv\"\r\n"
                + "Content-Type: text/csv\r\n\r\n" + "x".repeat(bytes) + "\r\n--wdm--\r\n";
    }

    private HttpResponse<String> send(String body, String identity, String contentType) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + DatasetUploadControllerTest.PATH))
                .header("Content-Type", contentType).header("X-Request-Id", "untrusted-marker");
        if (identity != null) request.header("X-Test-Identity", identity);
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    private static void check(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode(), response.body());
        var body = JsonMapper.builder().build().readTree(response.body());
        assertEquals(code, body.get("code").asString());
        if (status >= 400) assertEquals(status, body.get("status").asInt());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();
        assertEquals(requestId, body.get("requestId").asString());
        assertNotEquals("untrusted-marker", requestId);
        if (status >= 400) assertFalse(response.body().contains("snapshotId"));
    }
}
