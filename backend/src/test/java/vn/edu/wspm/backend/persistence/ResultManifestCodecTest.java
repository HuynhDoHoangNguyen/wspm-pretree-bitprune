package vn.edu.wspm.backend.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.backend.services.run.persistence.ResultManifestCodec;

class ResultManifestCodecTest {
    @Test void versionOneFixedVector() {
        assertEquals("61e5d4966b58069afd31dddbf8f54de90dbd0e5bcf3cb2c7d6f2d872c286f57b",ResultManifestCodec.digest(null));
    }
    @Test void sortedMapsAndFramedTypesPreserveIdentity() {
        var a=new LinkedHashMap<String,Object>(); a.put("b",List.of("ab","c"));a.put("a",1L);
        var b=new LinkedHashMap<String,Object>(); b.put("a",1L);b.put("b",List.of("ab","c"));
        assertEquals(ResultManifestCodec.digest(a),ResultManifestCodec.digest(b));
        assertNotEquals(ResultManifestCodec.digest(List.of("ab","c")),ResultManifestCodec.digest(List.of("a","bc")));
        assertNotEquals(ResultManifestCodec.digest(1L),ResultManifestCodec.digest("1"));
        assertNotEquals(ResultManifestCodec.digest(0.0),ResultManifestCodec.digest(-0.0));
        assertNotEquals(ResultManifestCodec.digest(List.of("a","b")),ResultManifestCodec.digest(List.of("b","a")));
        assertThrows(IllegalArgumentException.class,() -> ResultManifestCodec.digest(Double.NaN));
    }
    @Test void doubleOneUlpAndExactLongsRemainDistinguishable() {
        assertNotEquals(ResultManifestCodec.digest(0.1),ResultManifestCodec.digest(Math.nextUp(0.1)));
        assertNotEquals(ResultManifestCodec.digest(9007199254740992L),ResultManifestCodec.digest(9007199254740993L));
    }
}
