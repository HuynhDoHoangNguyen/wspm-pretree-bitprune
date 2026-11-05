package vn.edu.wspm.backend.services.dataset;

import jakarta.servlet.MultipartConfigElement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.dto.dataset.DatasetSnapshotResponse;

import java.util.Locale;
import java.util.Set;

/** Transport validation before durable acquisition; CSV content validation belongs to WDM-02. */
@Slf4j
@Service
public class CsvUploadService {
    private static final Set<String> TYPES = Set.of(
            "text/csv", "application/csv", "application/vnd.ms-excel", "application/octet-stream");
    private final long maxFileBytes;
    private final DatasetSnapshotService snapshots;

    public CsvUploadService(MultipartConfigElement config, DatasetSnapshotService snapshots) {
        // Use the servlet's actual limits, including Spring/environment overrides.
        if (config.getMaxFileSize() <= 0 || config.getMaxRequestSize() <= config.getMaxFileSize()) {
            throw new IllegalArgumentException(
                    "CSV multipart limits must be positive with request limit greater than file limit");
        }
        this.maxFileBytes = config.getMaxFileSize();
        this.snapshots = snapshots;
    }

    public DatasetSnapshotResponse upload(MultiValueMap<String, MultipartFile> files, boolean hasParameters) {
        var parts = files.get("file");
        if (hasParameters || files.size() != 1 || parts == null || parts.size() != 1) {
            throw ApplicationException.invalid(
                    "file", "INVALID_FILE_PART", "Supply exactly one file part and no other fields.");
        }
        MultipartFile file = parts.getFirst();
        if (file == null || file.isEmpty()) {
            throw ApplicationException.invalid("file", "EMPTY_FILE", "Supply a nonempty CSV file.");
        }
        if (file.getSize() > maxFileBytes) {
            throw new ApplicationException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "Upload exceeds configured file or request limits.");
        }
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            throw ApplicationException.invalid("file", "INVALID_FILENAME", "Supply a CSV filename.");
        }
        // Reject client paths; never resolve the original name on the filesystem or log it.
        if (name.length() > 255 || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
                || name.indexOf(':') >= 0 || name.codePoints().anyMatch(Character::isISOControl)) {
            throw ApplicationException.invalid("file", "INVALID_FILENAME", "Supply a plain filename.");
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(".csv")) unsupported();
        String type = file.getContentType();
        if (type != null) {
            try {
                MediaType media = MediaType.parseMediaType(type);
                if (!TYPES.contains((media.getType() + "/" + media.getSubtype())
                        .toLowerCase(Locale.ROOT))) unsupported();
            } catch (InvalidMediaTypeException invalid) {
                unsupported();
            }
        }
        log.debug("CSV upload passed transport checks: sizeBytes={}", file.getSize());
        return snapshots.acquire(file);
    }

    private static void unsupported() {
        throw new ApplicationException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "Upload requires a .csv filename and a supported CSV media type.");
    }
}
