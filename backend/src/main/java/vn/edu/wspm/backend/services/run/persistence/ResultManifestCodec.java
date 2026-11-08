package vn.edu.wspm.backend.services.run.persistence;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Versioned typed encoding: map keys sorted, lists ordered, UTF-8 length framed, doubles as raw bits. */
public final class ResultManifestCodec {
    public static final String VERSION = "WDM_RESULT_MANIFEST_V1";
    private ResultManifestCodec() {}
    public static String digest(Object value) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            encode(md, VERSION); encode(md, value);
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void length(MessageDigest md, long n) { md.update(ByteBuffer.allocate(8).putLong(n).array()); }
    private static void encode(MessageDigest md, Object value) {
        if (value == null) { md.update((byte) 0); return; }
        if (value instanceof String s) {
            md.update((byte) 1); var bytes = s.getBytes(StandardCharsets.UTF_8); length(md, bytes.length); md.update(bytes);
        } else if (value instanceof Double || value instanceof Float || value instanceof java.math.BigDecimal) {
            double n = ((Number) value).doubleValue();
            if (!Double.isFinite(n)) throw new IllegalArgumentException("Nonfinite manifest number");
            md.update((byte) 2); length(md, Double.doubleToRawLongBits(n));
        } else if (value instanceof Number n) {
            md.update((byte) 3); encode(md, n.toString());
        } else if (value instanceof Boolean b) { md.update((byte) (b ? 4 : 5));
        } else if (value instanceof Map<?, ?> map) {
            md.update((byte) 6); length(md, map.size());
            var sorted = new TreeMap<String, Object>();
            map.forEach((k, v) -> sorted.put((String) k, v));
            sorted.forEach((k, v) -> { encode(md, k); encode(md, v); });
        } else if (value instanceof List<?> list) {
            md.update((byte) 7); length(md, list.size()); list.forEach(v -> encode(md, v));
        } else throw new IllegalArgumentException("Unsupported manifest value");
    }
}
