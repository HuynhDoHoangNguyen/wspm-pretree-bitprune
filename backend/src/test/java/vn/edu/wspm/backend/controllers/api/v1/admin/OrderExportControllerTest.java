package vn.edu.wspm.backend.controllers.api.v1.admin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.json.JsonMapper;

import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.exception.GlobalExceptionHandler;
import vn.edu.wspm.backend.repositories.export.OrderExportRepository;
import vn.edu.wspm.backend.repositories.export.OrderExportRow;
import vn.edu.wspm.backend.services.export.OrderCsvWriter;
import vn.edu.wspm.backend.services.export.OrderExportService;
import vn.edu.wspm.backend.web.wdm.WdmAdminAuthorizer;
import vn.edu.wspm.backend.web.wdm.WdmRequestFilter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/** MVC + real service/writer, mocked data/auth collaborators; not Auth/DB E2E. */
@ExtendWith(MockitoExtension.class)
class OrderExportControllerTest {
    private static final String PATH = "/api/v1/admin/order-exports";
    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-11-01T00:00:00Z");
    private static final OffsetDateTime TO = FROM.plusDays(1);
    @Mock OrderExportRepository repository;
    @Mock ObjectProvider<WdmAdminAuthorizer> authorizers;
    @Mock WdmAdminAuthorizer authorizer;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        given(authorizers.stream()).willAnswer(call -> java.util.stream.Stream.of(authorizer));
        mvc = mvc(100000, 104857600);
    }

    private MockMvc mvc(int maxRows, int maxBytes) {
        var service = new OrderExportService(repository, new OrderCsvWriter(), maxRows, maxBytes);
        return MockMvcBuilders.standaloneSetup(new OrderExportController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new WdmRequestFilter(authorizers, JsonMapper.builder().build()))
                .build();
    }

    private static MockHttpServletRequestBuilder request() {
        return get(PATH)
                .param("fromTime", FROM.toString())
                .param("toTime", TO.toString())
                .accept("text/csv");
    }

    @Test
    void returnsExactContractFixtureWithDownloadHeaders() throws Exception {
        given(repository.findCompletedInPeriod(FROM, TO, 100000)).willReturn(fixtureRows());
        var result =
                mvc.perform(request().header("X-Request-Id", "untrusted-id"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType("text/csv;charset=UTF-8"))
                        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                        .andReturn();
        assertArrayEquals(
                ("\uFEFF" + new ClassPathResource("wdm/csv-v1/valid.csv")
                        .getContentAsString(StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8),
                result.getResponse().getContentAsByteArray());
        assertEquals(
                Integer.toString(result.getResponse().getContentAsByteArray().length),
                result.getResponse().getHeader(HttpHeaders.CONTENT_LENGTH));
        assertTrue(
                result.getResponse()
                        .getHeader(HttpHeaders.CONTENT_DISPOSITION)
                        .matches("attachment; filename=\"order-export-[0-9a-f-]{36}\\.csv\""));
        assertNotEquals("untrusted-id", result.getResponse().getHeader("X-Request-Id"));
        verify(authorizer).requireAdmin(any());
    }

    @Test
    void emptyExportContainsOnlyTheCanonicalHeader() throws Exception {
        given(repository.findCompletedInPeriod(FROM, TO, 100000)).willReturn(List.of());
        var result = mvc.perform(request()).andExpect(status().isOk()).andReturn();
        assertArrayEquals(
                ("\uFEFF" + new ClassPathResource("wdm/csv-v1/empty.csv")
                        .getContentAsString(StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8),
                result.getResponse().getContentAsByteArray());
    }

    @Test
    void exportsVietnameseWithOneLeadingUtf8BomAndPreservesCsvQuoting() throws Exception {
        given(repository.findCompletedInPeriod(FROM, TO, 100000)).willReturn(List.of(row(
                "order-vn", "1", "anon-vn", FROM.toInstant(), "product-vn", "SKU-VN",
                "Bàn phím \"Cơ\", tiếng Việt", 1, "100.00", "0.00", "100.00")));
        var result = mvc.perform(request()).andExpect(status().isOk()).andReturn();
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                java.util.Arrays.copyOf(bytes, 3));
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(-1, csv.indexOf('\uFEFF', 1));
        assertTrue(csv.startsWith("\uFEFForder_id,order_item_id,"));
        assertTrue(csv.contains(",\"Bàn phím \"\"Cơ\"\", tiếng Việt\",1,100.00,0.00,100.00,VND\r\n"));
        assertEquals(Integer.toString(bytes.length), result.getResponse().getHeader(HttpHeaders.CONTENT_LENGTH));
    }

    @Test
    void emptyExportByteLimitIncludesBomAndNeverSendsPartialAttachment() throws Exception {
        given(repository.findCompletedInPeriod(FROM, TO, 100000)).willReturn(List.of());
        int exactBytes = new ClassPathResource("wdm/csv-v1/empty.csv").getContentAsByteArray().length + 3;
        mvc(100000, exactBytes).perform(request()).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, Integer.toString(exactBytes)));
        for (int limit : new int[]{1, 2, exactBytes - 1}) {
            var result = mvc(100000, limit).perform(request()).andExpect(status().is(413)).andReturn();
            assertErrorMetadata(result);
        }
    }

    @Test
    void forwardsOffsetInputsWithoutLosingTheirInstant() throws Exception {
        OffsetDateTime start = OffsetDateTime.parse("2026-11-01T07:00:00+07:00");
        given(repository.findCompletedInPeriod(start, TO, 100000)).willReturn(List.of());
        mvc.perform(get(PATH).param("fromTime", start.toString()).param("toTime", TO.toString()))
                .andExpect(status().isOk());
        verify(repository).findCompletedInPeriod(start, TO, 100000);
        assertEquals(FROM.toInstant(), start.toInstant());
    }

    @Test
    void rejectsMissingDuplicateUnknownAndInvalidTimestampFilters() throws Exception {
        for (String value :
                List.of(
                        "",
                        "2026-11-01",
                        "2026-11-01T00:00:00",
                        "2026-11-01T00:00:00.0000000Z",
                        "2026-13-01T00:00:00Z")) {
            var result =
                    mvc.perform(
                                    get(PATH)
                                            .param("fromTime", value)
                                            .param("toTime", TO.toString())
                                            .accept("text/csv"))
                            .andExpect(status().isBadRequest())
                            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                            .andExpect(jsonPath("$.details[0].field").value("fromTime"))
                            .andReturn();
            assertErrorMetadata(result);
        }
        mvc.perform(get(PATH).param("toTime", TO.toString())).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).param("fromTime", FROM.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(request().param("fromTime", FROM.toString()))
                .andExpect(status().isBadRequest());
        var unknown =
                mvc.perform(request().param("sensitive-unknown-field", "sensitive-value"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.details[0].field").value("query"))
                        .andExpect(jsonPath("$.details[0].code").value("UNKNOWN_FIELD"))
                        .andExpect(jsonPath("$.details[0].message").value("Query contains an unsupported filter."))
                        .andReturn();
        assertFalse(unknown.getResponse().getContentAsString().contains("sensitive"));
        verifyNoInteractions(repository);
    }

    @Test
    void mapsRepositoryPeriodValidationToSafe400() throws Exception {
        given(repository.findCompletedInPeriod(TO, FROM, 100000))
                .willThrow(new IllegalArgumentException("internal detail"));
        var result =
                mvc.perform(
                                get(PATH)
                                        .param("fromTime", TO.toString())
                                        .param("toTime", FROM.toString()))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.details[0].field").value("period"))
                        .andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("internal detail"));
        assertErrorMetadata(result);
    }

    @Test
    void refusesOversizedRowsWithoutAttachmentOrPartialCsv() throws Exception {
        given(repository.findCompletedInPeriod(FROM, TO, 1))
                .willThrow(new OrderExportRepository.ResultLimitExceededException());
        var result =
                mvc(1, 104857600)
                        .perform(request())
                        .andExpect(status().is(413))
                        .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                        .andReturn();
        assertErrorMetadata(result);
    }

    @Test
    void countsUtf8BytesAndRejectsByteOverflowInsteadOfTruncating() throws Exception {
        var rows = fixtureRows();
        int exactBytes = new OrderCsvWriter().serialize(rows, 104857600).length;
        given(repository.findCompletedInPeriod(FROM, TO, 100000)).willReturn(rows);
        mvc(100000, exactBytes).perform(request()).andExpect(status().isOk());
        var result =
                mvc(100000, exactBytes - 1)
                        .perform(request())
                        .andExpect(status().is(413))
                        .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                        .andReturn();
        assertErrorMetadata(result);
    }

    @Test
    void sanitizesUnexpectedFailureBeforeSendingCsvHeaders() throws Exception {
        given(repository.findCompletedInPeriod(FROM, TO, 100000))
                .willThrow(
                        new DataAccessResourceFailureException("sensitive-marker /private/path"));
        var result =
                mvc.perform(request())
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                        .andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("sensitive-marker"));
        assertFalse(result.getResponse().getContentAsString().contains("/private/path"));
        assertErrorMetadata(result);
    }

    @Test
    void missingAuthProviderDisablesDataAccessBeforeValidation() throws Exception {
        given(authorizers.stream()).willAnswer(call -> java.util.stream.Stream.empty());
        var result =
                mvc.perform(
                                get(PATH)
                                        .param("unknown", "bad")
                                        .header("Authorization", "Bearer anything"))
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                        .andReturn();
        assertErrorMetadata(result);
        verifyNoInteractions(repository, authorizer);
    }

    @Test
    void consumerHonorsProvider401And403BeforeDataAccess() throws Exception {
        for (HttpStatus status : List.of(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)) {
            String code =
                    status == HttpStatus.UNAUTHORIZED ? "AUTHENTICATION_REQUIRED" : "ACCESS_DENIED";
            doThrow(new ApplicationException(ErrorCode.valueOf(code), "Access rejected."))
                    .when(authorizer)
                    .requireAdmin(any());
            var result =
                    mvc.perform(get(PATH))
                            .andExpect(status().is(status.value()))
                            .andExpect(jsonPath("$.code").value(code))
                            .andReturn();
            assertErrorMetadata(result);
        }
        verifyNoInteractions(repository);
    }

    @Test
    void unexpectedAuthFailureStillDeniesDataAccessAndHidesInternalDetails() throws Exception {
        doThrow(new IllegalStateException("sensitive-auth-marker"))
                .when(authorizer)
                .requireAdmin(any());
        var result =
                mvc.perform(request())
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                        .andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("sensitive-auth-marker"));
        assertErrorMetadata(result);
        verifyNoInteractions(repository);
    }

    @Test
    void unsupportedMethodRetainsAllowAndJsonEnvelope() throws Exception {
        var result =
                mvc.perform(post(PATH))
                        .andExpect(status().isMethodNotAllowed())
                        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                        .andReturn();
        assertTrue(result.getResponse().getHeader(HttpHeaders.ALLOW).contains("GET"));
        assertErrorMetadata(result);
        verifyNoInteractions(repository);
    }

    @Test
    void incompatibleAcceptReceivesAJsonError() throws Exception {
        var result =
                mvc.perform(get(PATH).accept("application/json"))
                        .andExpect(status().isNotAcceptable())
                        .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"))
                        .andReturn();
        assertErrorMetadata(result);
        verifyNoInteractions(repository);
    }

    private static void assertErrorMetadata(MvcResult result) throws Exception {
        var response = result.getResponse();
        assertEquals("application/json", response.getContentType());
        assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
        assertEquals(null, response.getHeader(HttpHeaders.CONTENT_DISPOSITION));
        var body = JsonMapper.builder().build().readTree(response.getContentAsByteArray());
        assertEquals(response.getStatus(), body.get("status").asInt());
        assertEquals(response.getHeader("X-Request-Id"), body.get("requestId").asString());
        assertEquals(PATH, body.get("path").asString());
        assertTrue(body.get("details").isArray());
        assertFalse(
                response.getContentAsString(StandardCharsets.UTF_8)
                        .contains("order_id,order_item_id"));
    }

    private static List<OrderExportRow> fixtureRows() {
        return List.of(
                row(
                        "order-a",
                        "9",
                        "anon-A",
                        FROM.toInstant(),
                        "product-laptop",
                        "=OLD-SKU",
                        "Laptop Việt, \"bản cũ\"\r\nDòng thứ hai",
                        2,
                        "15000000.00",
                        "1000000.00",
                        "29000000.00"),
                row(
                        "order-a",
                        "10",
                        "anon-A",
                        FROM.toInstant(),
                        "product-laptop",
                        "LAP-OLD",
                        "Laptop lich su",
                        1,
                        "15000000.00",
                        "0.00",
                        "15000000.00"),
                row(
                        "order-b",
                        "9007199254740993",
                        "anon-A",
                        FROM.toInstant(),
                        "product-mouse",
                        "MOUSE-OLD",
                        "Chuột cũ",
                        1,
                        "250000.50",
                        "0.50",
                        "250000.00"),
                row(
                        "order-c",
                        "9007199254740994",
                        "anon-B",
                        TO.toInstant().minusNanos(1000),
                        "product-mouse",
                        "MOUSE-OLD",
                        "Chuột cũ",
                        1,
                        "100.00",
                        "0.00",
                        "100.00"),
                row(
                        "order-c",
                        "9007199254740995",
                        "anon-B",
                        TO.toInstant().minusNanos(1000),
                        "product-bag",
                        "BAG-OLD",
                        "Túi laptop",
                        1,
                        "100.00",
                        "0.00",
                        "100.00"));
    }

    private static OrderExportRow row(
            String order,
            String line,
            String customer,
            Instant timestamp,
            String product,
            String sku,
            String name,
            long quantity,
            String price,
            String discount,
            String total) {
        return new OrderExportRow(
                order,
                new BigInteger(line),
                customer,
                timestamp,
                "COMPLETED",
                product,
                sku,
                name,
                quantity,
                new BigDecimal(price),
                new BigDecimal(discount),
                new BigDecimal(total),
                "VND");
    }
}
