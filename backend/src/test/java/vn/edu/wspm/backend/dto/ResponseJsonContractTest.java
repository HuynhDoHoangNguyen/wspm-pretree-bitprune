package vn.edu.wspm.backend.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.dto.auth.*;
import vn.edu.wspm.backend.dto.category.CategoryResponse;
import vn.edu.wspm.backend.dto.dataset.*;
import vn.edu.wspm.backend.dto.product.ProductResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Literal public JSON expectations; no reflection snapshots or persisted/generated golden files. */
class ResponseJsonContractTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private static final Instant NOW = Instant.parse("2026-11-05T01:02:03.123456Z");

    private void json(Object actual, String expected) {
        assertEquals(mapper.readTree(expected), mapper.readTree(mapper.writeValueAsString(actual)));
    }

    @Test void catalogSuccessEnvelopeRetainsNamesDecimalsAndExplicitNulls() {
        json(ApiResponse.success(List.of(new ProductResponse("9007199254740993", "Chuột", null,
                "ACTIVE", new BigDecimal("123456789.25"))), "request-1"), """
                {"code":"SUCCESS","message":"Request completed successfully.","requestId":"request-1",
                 "data":[{"productId":"9007199254740993","productName":"Chuột","categoryId":null,
                          "productStatus":"ACTIVE","price":123456789.25}]}
                """);
        json(new CategoryResponse("category-1", null), """
                {"categoryId":"category-1","categoryName":null}
                """);
        json(ApiResponse.success(List.of(), "request-1"), """
                {"code":"SUCCESS","message":"Request completed successfully.","data":[],"requestId":"request-1"}
                """);
    }

    @Test void authResponsesRetainClosedPublicIdentityAndTokenShape() {
        var roles = new ArrayList<>(List.of("CUSTOMER"));
        var identity = new IdentityResponse("user-1", "customer.test", null, "Nguyễn An", "ACTIVE", roles, false);
        roles.add("ADMIN");
        var response = new TokenResponse("synthetic-access-token", "Bearer", 900, NOW, identity);
        json(response, """
                {"accessToken":"synthetic-access-token","tokenType":"Bearer","expiresIn":900,
                 "expiresAt":"2026-11-05T01:02:03.123456Z",
                 "user":{"userId":"user-1","username":"customer.test","email":null,"displayName":"Nguyễn An",
                         "status":"ACTIVE","roles":["CUSTOMER"],"emailVerified":false}}
                """);
        assertThrows(UnsupportedOperationException.class, () -> identity.roles().add("ADMIN"));
        assertFalse(response.toString().contains("synthetic-access-token"));
        assertFalse(identity.toString().contains("customer.test"));
        assertFalse(ApiResponse.success(response, "request-1").toString().contains("synthetic-access-token"));
    }

    @Test void acquisitionResponsePreservesStringCountsUtcPrecisionAndUnknownStatistics() {
        json(new DatasetSnapshotResponse("snapshot-1", "CSV", "đơn hàng.csv", "PENDING", "NOT_STARTED",
                "NOT_COMPUTED", "a".repeat(64), "9007199254740993", null, null, null, NOW), """
                {"snapshotId":"snapshot-1","sourceType":"CSV","sourceName":"đơn hàng.csv",
                 "validationStatus":"PENDING","conversionStatus":"NOT_STARTED","statisticsStatus":"NOT_COMPUTED",
                 "rawSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                 "rawSizeBytes":"9007199254740993","sequenceCount":null,"fromTime":null,"toTime":null,
                 "createdAt":"2026-11-05T01:02:03.123456Z"}
                """);
    }

    @Test void detailResponseKeepsExplicitProvenanceAndOpaqueStringCountWithoutInternalFields() {
        json(new SnapshotDetailResponse("snapshot-1", "DATABASE", "export.csv", "VALID", "NOT_STARTED", "COMPUTED",
                null, "42", "9007199254740993", NOW, NOW, NOW, "UNAVAILABLE",
                new SnapshotDetailResponse.Provenance("wdm-order-csv-v1", "DATABASE_EXPORT", "UTC",
                        "ordered_at", "COMPLETED_CURRENT_AT_ACQUISITION")), """
                {"snapshotId":"snapshot-1","sourceType":"DATABASE","sourceName":"export.csv","validationStatus":"VALID",
                 "conversionStatus":"NOT_STARTED","statisticsStatus":"COMPUTED","rawSha256":null,"rawSizeBytes":"42",
                 "sequenceCount":"9007199254740993","fromTime":"2026-11-05T01:02:03.123456Z",
                 "toTime":"2026-11-05T01:02:03.123456Z","createdAt":"2026-11-05T01:02:03.123456Z",
                 "rawAvailability":"UNAVAILABLE","provenance":{"contractVersion":"wdm-order-csv-v1",
                 "acquisition":"DATABASE_EXPORT","sourceTimeZone":"UTC","timeField":"ordered_at",
                 "statusPolicy":"COMPLETED_CURRENT_AT_ACQUISITION"}}
                """);
    }
}
