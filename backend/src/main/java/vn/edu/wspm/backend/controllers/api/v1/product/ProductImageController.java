package vn.edu.wspm.backend.controllers.api.v1.product;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** Read-only flat raster image directory; never delegates to generic static resource serving. */
@RestController
public class ProductImageController {
    private static final String PREFIX = "/uploads/products/";
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final Map<String, String> FORMATS = Map.of(
            "jpg", "JPEG", "jpeg", "JPEG", "png", "PNG", "gif", "GIF");
    private final Path root;

    public ProductImageController(@Value("${catalog.images.root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @GetMapping("/uploads/products/**")
    public ResponseEntity<byte[]> read(HttpServletRequest request) {
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        if (!uri.startsWith(PREFIX)) return ResponseEntity.notFound().build();
        String filename = uri.substring(PREFIX.length());
        // Reject encoded names, separators, dot segments, alternate streams and active content.
        if (!filename.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]*\\.(?i:jpg|jpeg|png|gif)")
                || filename.contains("..")) return ResponseEntity.notFound().build();
        String format = FORMATS.get(filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT));
        try {
            Path directory = root.toRealPath();
            Path file = directory.resolve(filename);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || !file.toRealPath().getParent().equals(directory)) return ResponseEntity.notFound().build();
            byte[] bytes;
            try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                bytes = input.readNBytes(MAX_BYTES + 1);
            }
            if (bytes.length > MAX_BYTES) return ResponseEntity.notFound().build();
            // Inspect actual raster content, never trust the filename or OS MIME guess.
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) return ResponseEntity.notFound().build();
                var reader = readers.next();
                try {
                    if (!format.equalsIgnoreCase(reader.getFormatName())) return ResponseEntity.notFound().build();
                    reader.setInput(input);
                    if (reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0) return ResponseEntity.notFound().build();
                } finally { reader.dispose(); }
            }
            String mime = "image/" + (format.equals("JPEG") ? "jpeg" : format.toLowerCase(Locale.ROOT));
            return ResponseEntity.ok().header("Content-Type", mime)
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Cache-Control", "no-cache").contentLength(bytes.length).body(bytes);
        } catch (IOException inaccessible) {
            return ResponseEntity.notFound().build();
        }
    }
}
