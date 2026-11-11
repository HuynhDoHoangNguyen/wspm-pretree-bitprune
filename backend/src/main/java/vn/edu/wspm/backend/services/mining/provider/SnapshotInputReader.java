package vn.edu.wspm.backend.services.mining.provider;

import java.time.LocalDateTime;
import java.util.*;
import java.nio.charset.StandardCharsets;
import vn.edu.wspm.backend.services.mining.PreparedSnapshotMiner;

/** Each call must return a fresh read snapshot, not an ORM first-level cached entity. */
public interface SnapshotInputReader {
    record Metadata(String id, String sourceType, String validationStatus, String rawReference,
                    String rawHash, String rawSize, String contractVersion, String acquisition,
                    String sourceTimeZone, String timeField, String statusPolicy,
                    LocalDateTime fromTime, LocalDateTime toTime, LocalDateTime createdAt,
                    String sourceName, String completeReport) {
        // No @Version exists in V2. This binds all metadata consumed here, including the complete report.
        public String binding() {
            var framed = new StringBuilder();
            for (Object value : Arrays.asList(id, sourceType, validationStatus, rawReference, rawHash,
                    rawSize, contractVersion, acquisition, sourceTimeZone, timeField, statusPolicy,
                    fromTime, toTime, createdAt, sourceName, completeReport)) {
                if (value == null) framed.append("N;");
                else { String s = value.toString(); framed.append(s.getBytes(StandardCharsets.UTF_8).length).append(':').append(s); }
            }
            return PreparedSnapshotMiner.sha256(framed.toString().getBytes(StandardCharsets.UTF_8));
        }
        @Override public String toString() { return "SnapshotMetadata[redacted]"; }
    }
    /** matches use the database column's actual equality/collation; exact equality is checked by provider. */
    record Read(Optional<Metadata> metadata, Map<String, List<String>> productMatches) {
        public Read {
            Objects.requireNonNull(metadata);
            var copy = new HashMap<String,List<String>>();
            productMatches.forEach((id, matches) -> copy.put(id, List.copyOf(matches)));
            productMatches = Collections.unmodifiableMap(copy);
        }
    }
    Read read(String snapshotId, Set<String> productIds);
}
