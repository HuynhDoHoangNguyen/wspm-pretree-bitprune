package vn.edu.wspm.backend.controllers.api.v1.product;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.edu.wspm.backend.web.auth.ApiSecurityFilter;
import vn.edu.wspm.backend.web.auth.SharedRequestAuthenticator;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real HTTP/servlet image boundary, isolated raster fixtures; no application DB or migration. */
@SpringBootTest(classes = ProductImageHttpTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration")
class ProductImageHttpTest {
    @TempDir static Path directory;
    @LocalServerPort int port;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("catalog.images.root", () -> directory.toString());
    }

    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration
    @Import({ProductImageController.class, ApiSecurityFilter.class})
    static class Application {
        @Bean SharedRequestAuthenticator authenticator() { return mock(SharedRequestAuthenticator.class); }
    }

    @BeforeAll static void fixtures() throws Exception {
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpg", directory.resolve("sample.jpg").toFile());
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", directory.resolve("sample.png").toFile());
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "gif", directory.resolve("sample.gif").toFile());
        Files.writeString(directory.resolve("fake.jpg"), "<html>active content</html>");
        Files.copy(directory.resolve("sample.png"), directory.resolve("wrong.jpg"));
        Files.writeString(directory.resolve("secret.txt"), "private-test-marker");
        Files.createDirectory(directory.resolve("folder.jpg"));
    }

    @Test void publicRasterGetReturnsExactBytesAndTrustedMimeWithoutJwt() throws Exception {
        for (String extension : new String[]{"jpg", "png", "gif"}) {
            var result = send("/uploads/products/sample." + extension, "GET");
            assertEquals(200, result.statusCode());
            assertEquals("image/" + (extension.equals("jpg") ? "jpeg" : extension), result.headers().firstValue("Content-Type").orElseThrow());
            assertEquals("nosniff", result.headers().firstValue("X-Content-Type-Options").orElseThrow());
            assertArrayEquals(Files.readAllBytes(directory.resolve("sample." + extension)), result.body());
        }
    }

    @Test void missingDisallowedMismatchedAndDirectoryRequestsAre404() throws Exception {
        for (String path : new String[]{"missing.jpg", "fake.jpg", "wrong.jpg", "secret.txt",
                "folder.jpg", "", "folder.jpg/sample.jpg", "sample.svg", "sample.jpg/extra"}) {
            assertEquals(404, send("/uploads/products/" + path, "GET").statusCode(), path);
        }
        assertEquals(404, send("/uploads/secret.txt", "GET").statusCode());
        assertEquals(405, send("/uploads/products/sample.jpg", "POST").statusCode());
        assertEquals(0, send("/uploads/products/sample.jpg", "HEAD").body().length);
    }

    @Test void traversalAndEncodedTraversalNeverReturnFileContent() throws Exception {
        for (String path : new String[]{"../secret.txt", "%2e%2e/secret.txt", "%252e%252e%252fsecret.txt",
                "..%5csecret.txt", "%2fsecret.txt", "sample.jpg%00", "sample.jpg;extra"}) {
            int status = send("/uploads/products/" + path, "GET").statusCode();
            assertTrue(status == 400 || status == 404, path + ": " + status);
        }
    }

    private HttpResponse<byte[]> send(String path, String method) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray());
        }
    }
}
