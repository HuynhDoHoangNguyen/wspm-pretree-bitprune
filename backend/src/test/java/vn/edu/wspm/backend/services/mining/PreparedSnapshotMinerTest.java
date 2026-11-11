package vn.edu.wspm.backend.services.mining;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.backend.services.mining.PreparedSnapshotMiner.*;

class PreparedSnapshotMinerTest {
    final PreparedSnapshotMiner miner=new PreparedSnapshotMiner();
    final String spmf="1 2 -1 -2\n1 -1 2 -1 -2\n";
    Input input(String data,MinimumSupport threshold,Map<Integer,DictionaryItem> dictionary) {
        byte[] bytes=data.getBytes(StandardCharsets.UTF_8);
        return new Input("fixture-snapshot","a".repeat(64),bytes,PreparedSnapshotMiner.sha256(bytes),
                "TEST_PRECONVERTED_ONLY","TEST_SOURCE_REVISION",dictionary,threshold);
    }
    Map<Integer,DictionaryItem> dictionary() { return Map.of(1,new DictionaryItem("L","Snapshot laptop","SKU-L"),2,new DictionaryItem("M","Snapshot mouse","SKU-M")); }
    @Test void realEngineKeepsFullKnownSupportMapAndItemsetBoundaryWithoutPublication() {
        var result=miner.mine(input(spmf,MinimumSupport.absolute(.4),dictionary()));
        assertEquals(Map.of("<(1)>",2,"<(2)>",2,"<(1 2)>",1,"<(1)(2)>",1),result.canonicalSupports());
        assertEquals(.4,result.engine().minimumSupport().effectiveThreshold());
        assertEquals(.5,result.engine().globalMeanWeight().orElseThrow());
        assertEquals("SUPPORT_ONLY",result.engine().retentionPolicy().name());
        assertTrue(result.publishBlockers().contains("PATTERN_WEIGHT_POLICY_UNCONFIRMED"));
        assertEquals("Snapshot laptop",result.input().dictionary().get(1).nameSnapshot());
    }
    @Test void zeroAcceptedPatternsIsPreservedAndNoDummyRowsAreCreated() {
        var result=miner.mine(input(spmf,MinimumSupport.absolute(10),dictionary()));
        assertTrue(result.canonicalSupports().isEmpty());assertEquals(0,result.engine().statistics().patternCount());
        miner.verifyRoundTrip(result,List.of());
    }
    @Test void roundTripRejectsWrongSupportMissingReplacedAndDuplicatePatternEvenWhenCountsMatch() {
        var result=miner.mine(input(spmf,MinimumSupport.absolute(.4),dictionary()));
        var stored=result.canonicalSupports().entrySet().stream().map(e->new StoredPattern(e.getKey(),e.getValue().toString())).toList();
        miner.verifyRoundTrip(result,stored);
        var changed=new ArrayList<>(stored);changed.set(0,new StoredPattern(stored.getFirst().canonical(),"99"));
        assertEquals("ROUND_TRIP_MISMATCH",assertThrows(IllegalArgumentException.class,()->miner.verifyRoundTrip(result,changed)).getMessage());
        var replaced=new ArrayList<>(stored);replaced.set(0,new StoredPattern("<(999)>","2"));
        assertThrows(IllegalArgumentException.class,()->miner.verifyRoundTrip(result,replaced));
        var duplicate=new ArrayList<>(stored);duplicate.set(0,stored.get(1));
        assertEquals("STORED_IDENTITY_DUPLICATE",assertThrows(IllegalArgumentException.class,()->miner.verifyRoundTrip(result,duplicate)).getMessage());
        assertThrows(IllegalArgumentException.class,()->miner.verifyRoundTrip(result,stored.subList(1,stored.size())));
    }
    @Test void dictionaryMustExactlyCoverConvertedItemsAndRespectV2ProductUniqueness() {
        assertThrows(IllegalArgumentException.class,()->miner.mine(input(spmf,MinimumSupport.absolute(.4),Map.of(1,dictionary().get(1)))));
        assertThrows(IllegalArgumentException.class,()->miner.mine(input(spmf,MinimumSupport.absolute(.4),Map.of(1,dictionary().get(1),2,dictionary().get(1)))));
        assertEquals("V2_ITEM_ID_DOMAIN_UNSUPPORTED",assertThrows(IllegalArgumentException.class,()->miner.mine(input("0 -1 -2",MinimumSupport.absolute(.1),Map.of(0,dictionary().get(1))))).getMessage());
    }
    @Test void immutableBytesAndExactHashAreVerifiedAndR6IsNotDefined() {
        byte[] bytes=spmf.getBytes(StandardCharsets.UTF_8);var in=new Input("S","a".repeat(64),bytes,PreparedSnapshotMiner.sha256(bytes),"TEST","TEST",dictionary(),MinimumSupport.absolute(.4));
        bytes[0]='9';in.spmf()[0]='8';assertEquals(4,miner.mine(in).canonicalSupports().size());
        var wrong=new Input("S","a".repeat(64),bytes,in.sequenceSha256(),"TEST","TEST",dictionary(),MinimumSupport.absolute(.4));
        assertEquals("SEQUENCE_HASH_MISMATCH",assertThrows(IllegalArgumentException.class,()->miner.mine(wrong)).getMessage());
        assertEquals("ZERO_EFFECTIVE_THRESHOLD_UNSUPPORTED",assertThrows(IllegalArgumentException.class,()->miner.mine(input(spmf,MinimumSupport.absolute(0),dictionary()))).getMessage());
        assertEquals("ZERO_EFFECTIVE_THRESHOLD_UNSUPPORTED",assertThrows(IllegalArgumentException.class,()->miner.mine(input(spmf,MinimumSupport.percentage(Double.MIN_VALUE),dictionary()))).getMessage());
    }
    @Test void invalidUtf8AndSpmfAreRejectedWithoutRawDatasetInException() {
        assertEquals("SEQUENCE_FORMAT_INVALID",assertThrows(IllegalArgumentException.class,()->miner.mine(input("1 -2",MinimumSupport.absolute(.4),dictionary()))).getMessage());
        byte[] invalid={(byte)0xc3,0x28};
        var in=new Input("S","a".repeat(64),invalid,PreparedSnapshotMiner.sha256(invalid),"TEST","TEST",Map.of(),MinimumSupport.absolute(.4));
        assertEquals("SEQUENCE_FORMAT_INVALID",assertThrows(IllegalArgumentException.class,()->miner.mine(in)).getMessage());
    }
    @Test void unequalItemsetProposalFixtureDoesNotChangeFrozenAcceptance() {
        // Occurrences A=4,B=2,C=1,D=3, total length10. No recommendation-weight formula is implemented.
        var mapping=new TreeMap<>(dictionary());mapping.put(3,new DictionaryItem("C","Snapshot C","SKU-C"));mapping.put(4,new DictionaryItem("D","Snapshot D","SKU-D"));
        var result=miner.mine(input("1 2 -1 1 3 -1 2 -1 -2\n1 4 -1 1 4 -1 4 -1 -2\n",MinimumSupport.absolute(.2),mapping));
        assertEquals(.25,result.engine().globalMeanWeight().orElseThrow());
        assertEquals(.2,result.engine().minimumSupport().effectiveThreshold());
        assertEquals(1,result.canonicalSupports().get("<(1 2)(3)>").intValue());
        assertEquals(1,result.canonicalSupports().get("<(1 3)(2)>").intValue());
        assertTrue(result.publishBlockers().contains("PATTERN_WEIGHT_POLICY_UNCONFIRMED"));
    }
}
