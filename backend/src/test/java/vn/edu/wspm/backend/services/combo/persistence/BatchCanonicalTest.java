package vn.edu.wspm.backend.services.combo.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.math.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class BatchCanonicalTest {
    @Test void lengthFramedGoldenStringDoesNotUseDelimiterConcatenation() {
        assertArrayEquals(new byte[]{'S',0,0,0,1,'A'},BatchCanonical.encode("A",6));
        assertFalse(Arrays.equals(BatchCanonical.encode(List.of("a|b","c"),100),BatchCanonical.encode(List.of("a","b|c"),100)));
    }
    @Test void mapsAndSetsAreOrderIndependentButItemsetListsKeepOrder() {
        var a=new LinkedHashMap<String,Object>();a.put("b",2);a.put("a",1);
        var b=new LinkedHashMap<String,Object>();b.put("a",1);b.put("b",2);
        assertArrayEquals(BatchCanonical.encode(a,200),BatchCanonical.encode(b,200));
        assertArrayEquals(BatchCanonical.encode(new LinkedHashSet<>(List.of("b","a")),200),BatchCanonical.encode(new LinkedHashSet<>(List.of("a","b")),200));
        assertFalse(Arrays.equals(BatchCanonical.encode(List.of(List.of("L"),List.of("M")),200),BatchCanonical.encode(List.of(List.of("M"),List.of("L")),200)));
    }
    @Test void numericNormalizationPreservesExactValuesAndDistinguishesNull() {
        assertArrayEquals(BatchCanonical.encode(new BigDecimal("1.00"),100),BatchCanonical.encode(new BigDecimal("1.0"),100));
        assertArrayEquals(BatchCanonical.encode(1L,100),BatchCanonical.encode(BigInteger.ONE,100));
        assertArrayEquals(BatchCanonical.encode(-0.0,100),BatchCanonical.encode(0.0,100));
        assertFalse(Arrays.equals(BatchCanonical.encode(null,100),BatchCanonical.encode(0.0,100)));
        assertFalse(Arrays.equals(BatchCanonical.encode(new BigInteger("9007199254740993"),100),BatchCanonical.encode(new BigInteger("9007199254740992"),100)));
    }
    @Test void invalidUnicodeNonfiniteAndOverLimitAreRejectedWithoutTruncation() {
        assertThrows(IllegalArgumentException.class,()->BatchCanonical.encode("bad\uD800",100));
        assertThrows(IllegalArgumentException.class,()->BatchCanonical.encode(Double.NaN,100));
        assertThrows(IllegalArgumentException.class,()->BatchCanonical.encode("A",5));
    }
}
