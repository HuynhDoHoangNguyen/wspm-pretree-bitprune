package vn.edu.wspm.backend.entities;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PatternMetricCompatibilityTest {
    @Test void missingMetricStaysNullAndExplicitZeroIsNotAConversionDefault() {
        var entity=new PatternEntity(null,2L,null,1);entity.setPatternMetric(null,null);
        assertNull(entity.getWeight());assertNull(entity.getPatternWeightPolicy());
        entity.setPatternMetric(0.0,"UNCONFIRMED_REFERENCE");
        assertEquals(0.0,entity.getWeight());assertEquals("UNCONFIRMED_REFERENCE",entity.getPatternWeightPolicy());
        entity.setPatternMetric(null,null);assertNull(entity.getWeight());assertNull(entity.getPatternWeightPolicy());
    }
    @Test void invalidPairsFailBeforeMutatingEitherField() {
        var entity=new PatternEntity(null,2L,null,1);entity.setPatternMetric(.4,"UNCONFIRMED_REFERENCE");
        for(Object[] pair:new Object[][]{{.2,null},{null,"policy"},{.2," "},{.2,"\t"},{Double.NaN,"policy"},
                {Double.POSITIVE_INFINITY,"policy"},{.2,"bad\0policy"},{.2,"x".repeat(81)}}) {
            assertThrows(IllegalArgumentException.class,()->entity.setPatternMetric((Double)pair[0],(String)pair[1]));
            assertEquals(.4,entity.getWeight());assertEquals("UNCONFIRMED_REFERENCE",entity.getPatternWeightPolicy());
        }
    }
}
