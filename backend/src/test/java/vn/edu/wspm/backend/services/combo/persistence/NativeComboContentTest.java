package vn.edu.wspm.backend.services.combo.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class NativeComboContentTest {
    @Test void existingSmallContentRetainsExactV1Bytes() {
        var value=Map.of("version","COMBO_DRAFT_CONTENT_V1","payload","small");
        assertArrayEquals(BatchCanonical.encode(value,1048576),NativeComboContent.envelope(value,1048576));
    }
    @Test void largeNativeContentBindsEveryByteWithoutIncreasingStoredLimit() {
        var full=Map.of("payload","x".repeat(1048576));
        byte[] raw=BatchCanonical.encode(full,8388608);
        byte[] expected=BatchCanonical.encode(Map.of("version","NATIVE_COMBO_CONTENT_DIGEST_V2",
                "canonicalVersion","COMBO_DRAFT_CONTENT_V1","payloadSha256",JdbcDraftBatchStore.hash(raw),"payloadSize",raw.length),1048576);
        assertArrayEquals(expected,NativeComboContent.envelope(full,1048576));
        assertFalse(Arrays.equals(expected,NativeComboContent.envelope(Map.of("payload","y"+"x".repeat(1048575)),1048576)));
        assertThrows(IllegalArgumentException.class,()->NativeComboContent.envelope(Map.of("payload","x".repeat(8388608)),1048576));
    }
}
