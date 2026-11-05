package vn.edu.wspm.backend.web.wdm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import vn.edu.wspm.backend.exception.GlobalExceptionHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

class WdmExceptionHandlerTest {
    @Test
    void neverAppendsJsonToCommittedArtifactBytes() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/admin/order-exports");
        var response = new MockHttpServletResponse();
        byte[] partial = "order_id,order_item_id\r\n".getBytes(StandardCharsets.UTF_8);
        response.getOutputStream().write(partial);
        response.setCommitted(true);
        assertNull(
                new GlobalExceptionHandler()
                        .handle(new IOException("Synthetic transfer failure"), request, response));
        assertArrayEquals(partial, response.getContentAsByteArray());
    }

    @Test
    void leavesLegacyExceptionResolutionUnchanged() {
        var request = new MockHttpServletRequest("GET", "/legacy/products");
        var failure = new IllegalStateException("Legacy failure");
        var thrown =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new GlobalExceptionHandler()
                                        .handle(failure, request, new MockHttpServletResponse()));
        assertSame(failure, thrown);
    }
}
