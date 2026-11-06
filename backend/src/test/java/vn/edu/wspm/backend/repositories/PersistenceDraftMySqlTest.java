package vn.edu.wspm.backend.repositories;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import vn.edu.wspm.backend.services.mining.PreparedSnapshotMiner;
import vn.edu.wspm.backend.services.mining.conversion.*;
import vn.edu.wspm.backend.testsupport.OwnedMySql;

/** Tests the DELIVERED draft file, not duplicated test DDL. No writer/publisher/concurrency claims. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistenceDraftMySqlTest {
    OwnedMySql mysql;JdbcTemplate jdbc;String run;
    static final LocalDateTime TIME=LocalDateTime.parse("2026-01-01T00:00:00");
    @BeforeAll void bootstrapAndApplyDeliveredDraft() throws Exception {
        mysql=new OwnedMySql();
        try {
            mysql.createV2Tables();jdbc=new JdbcTemplate(mysql.dataSource());
            assertTrue(jdbc.queryForObject("SELECT @@max_allowed_packet",Long.class)>=33554432,
                    "Owned test server must carry draft aggregate-limit boundary payloads");
            jdbc.update("INSERT INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,validation_status,conversion_report_json,sequence_count) VALUES('S','CSV','fixture','VALID','{}',1),('OTHER','CSV','other','VALID','{}',1)");
            createRun("legacy","S");
            pattern("legacy","<(1)>",.42,"LEGACY_UNCONFIRMED_REFERENCE");
            Path relative=Path.of("docs/research/TV2-INT-08E_persistence_proposed.sql");
            Path draft=Files.isRegularFile(relative)?relative:Path.of("..").resolve(relative);
            assertTrue(Files.isRegularFile(draft),"Delivered draft SQL must exist outside Flyway scan path");
            try(var c=mysql.dataSource().getConnection()) {ScriptUtils.executeSqlScript(c,new FileSystemResource(draft));}
        }catch(Exception | AssertionError failure){mysql.close();throw failure;}
    }
    @BeforeEach void newRun() {run="r"+UUID.randomUUID().toString().replace("-","");createRun(run,"S");}
    @AfterAll void close(){if(mysql!=null)mysql.close();}
    void createRun(String id,String snapshot) {
        jdbc.update("INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,minsup_configured,effective_threshold,mean_w) VALUES(?,?,'WSPM_PRETREE','QUEUED','schema fixture','ABSOLUTE',1,1,.5)",id,snapshot);
    }
    void pattern(String id,String canonical,Double weight,String policy) {
        jdbc.update("INSERT INTO PATTERN(run_id,canonical_pattern,canonical_pattern_hash,support_abs,support_ratio,weighted_support,pattern_item_count,itemset_count,pattern_weight,pattern_weight_policy) VALUES(?,?,SHA2(?,256),9007199254740993,.5,1,1,1,?,?)",id,canonical,canonical,weight,policy);
    }
    @Test void oldMetricAndExistingIndexesRemainUnchangedAndNullPairIsDistinctFromZero() {
        var old=jdbc.queryForMap("SELECT pattern_weight,pattern_weight_policy,support_abs FROM PATTERN WHERE run_id='legacy'");
        assertEquals(.42,((Number)old.get("pattern_weight")).doubleValue());
        assertEquals("LEGACY_UNCONFIRMED_REFERENCE",old.get("pattern_weight_policy"));
        assertEquals("9007199254740993",old.get("support_abs").toString());
        pattern(run,"<(2)>",null,null);
        assertNull(jdbc.queryForMap("SELECT pattern_weight,pattern_weight_policy FROM PATTERN WHERE run_id=?",run).get("pattern_weight"));
        // Explicit metric 0 with policy is a different SQL state; not an endorsed runtime substitute.
        pattern(run,"<(3)>",0.0,"UNCONFIRMED_REFERENCE");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM PATTERN WHERE run_id=? AND pattern_weight IS NULL",Integer.class,run));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM PATTERN WHERE run_id=? AND pattern_weight=0",Integer.class,run));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='PATTERN' AND index_name='idx_v2_pattern_run_rank'",Integer.class));
    }
    @Test void halfNullAndBlankPolicyPairsAreRejectedWithoutWeakeningExistingChecks() {
        violation(3819,()->pattern(run,"<(2)>",.3,null));
        violation(3819,()->pattern(run,"<(3)>",null,"UNCONFIRMED_REFERENCE"));
        assertThrows(DataAccessException.class,()->pattern(run,"<(4)>",.3,""));
        assertThrows(DataAccessException.class,()->pattern(run,"<(5)>",.3,"   "));
        pattern(run,"<(6)>",null,null);
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE PATTERN SET support_ratio=2 WHERE run_id=?",run));
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE PATTERN SET pattern_item_count=0 WHERE run_id=?",run));
    }
    record Artifacts(byte[] raw,ConvertedOrderSnapshot converted,byte[] stats,byte[] manifest) {}
    Artifacts artifacts() {
        byte[] raw=(OrderSnapshotConverter.HEADER+"\nO,1,c,2026-01-01T00:00:00.000000Z,COMPLETED,P,Historic SKU,Tên Việt,1,1.00,0.00,1.00,VND\n").getBytes(StandardCharsets.UTF_8);
        var c=new OrderSnapshotConverter().convert(raw,new OrderSnapshotConverter.Limits(10000,20,300,20000));
        return new Artifacts(raw,c,"{\"schemaFixture\":true,\"sequenceCount\":\"1\"}\n".getBytes(StandardCharsets.UTF_8),
                "{\"sealVersion\":1,\"schemaFixtureOnly\":true}\n".getBytes(StandardCharsets.UTF_8));
    }
    String seal(String snapshot,String id,Artifacts a) {
        jdbc.update("""
            INSERT INTO SNAPSHOT_CONVERSION_SEAL(conversion_id,snapshot_id,conversion_policy,seal_version,seal_digest,
              raw_metadata_binding,source_type,acquisition,source_namespace,source_verification_scheme,
              raw_bytes,raw_sha256,raw_size,sequence_bytes,sequence_sha256,sequence_size,mapping_bytes,mapping_sha256,mapping_size,
              statistics_bytes,statistics_sha256,sequence_count,dictionary_count,manifest_bytes,created_at)
            VALUES(?,?,?,1,UNHEX(?),UNHEX(?),'CSV','CSV_UPLOAD','schema-fixture','CSV_UPLOAD_OPERATOR_ASSIGNED_V1',
              ?,UNHEX(?),?,?,UNHEX(?),?,?,UNHEX(?),?,?,UNHEX(?),1,1,?,?)
            """,id,snapshot,OrderSnapshotConverter.POLICY,hash(a.manifest()),"0".repeat(64),
                a.raw(),hash(a.raw()),a.raw().length,a.converted().spmf(),a.converted().sequenceSha256(),a.converted().spmf().length,
                a.converted().mapping(),a.converted().mappingSha256(),a.converted().mapping().length,
                a.stats(),hash(a.stats()),a.manifest(),TIME);
        return id;
    }
    String uniqueSeal(String snapshot){return seal(snapshot,"c"+UUID.randomUUID().toString().replace("-",""),artifacts());}
    void input(String id,String snapshot,String conversion,byte[] request) {
        byte[] manifest="{\"schemaInputVersion\":1}\n".getBytes(StandardCharsets.UTF_8);
        jdbc.update("INSERT INTO MINING_RUN_INPUT_BINDING(run_id,snapshot_id,conversion_id,request_identity,input_version,input_digest,engine_revision,engine_digest_scheme,engine_digest,manifest_bytes,created_at) VALUES(?,?,?,?,1,UNHEX(?),'fixture-build','JAR_ENTRIES_SHA256_V1',UNHEX(?),?,?)",
                id,snapshot,conversion,request,hash(manifest),"1".repeat(64),manifest,TIME);
    }
    String prepareInput() {
        // One seal/snapshot+policy: reuse existing persisted fixture for subsequent tests.
        var ids=jdbc.queryForList("SELECT conversion_id FROM SNAPSHOT_CONVERSION_SEAL WHERE snapshot_id='S'",String.class);
        String seal=ids.isEmpty()?uniqueSeal("S"):ids.getFirst();
        input(run,"S",seal,run.getBytes(StandardCharsets.UTF_8));return seal;
    }
    @Test void actualConverterBytesHashesAndSizesRoundTripAndTamperingIsRejected() {
        var a=artifacts();String snapshot="s"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,validation_status,conversion_report_json,sequence_count) VALUES(?,'CSV','fixture','VALID','{}',1)",snapshot);
        String id=seal(snapshot,"roundtrip",a);
        jdbc.query("SELECT raw_bytes,sequence_bytes,mapping_bytes,raw_sha256,sequence_sha256,mapping_sha256 FROM SNAPSHOT_CONVERSION_SEAL WHERE conversion_id=?",rs->{
            assertArrayEquals(a.raw(),rs.getBytes(1));assertArrayEquals(a.converted().spmf(),rs.getBytes(2));assertArrayEquals(a.converted().mapping(),rs.getBytes(3));
            assertEquals(hash(rs.getBytes(1)),HexFormat.of().formatHex(rs.getBytes(4)));
            assertEquals(hash(rs.getBytes(2)),HexFormat.of().formatHex(rs.getBytes(5)));
            assertEquals(hash(rs.getBytes(3)),HexFormat.of().formatHex(rs.getBytes(6)));
        },id);
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE SNAPSHOT_CONVERSION_SEAL SET raw_bytes=? WHERE conversion_id=?",new byte[]{0,1,2},id));
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE SNAPSHOT_CONVERSION_SEAL SET raw_size=raw_size+1 WHERE conversion_id=?",id));
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE SNAPSHOT_CONVERSION_SEAL SET sequence_count=0 WHERE conversion_id=?",id));
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE SNAPSHOT_CONVERSION_SEAL SET acquisition='DATABASE_EXPORT' WHERE conversion_id=?",id));
    }
    @Test void sealUniquenessAndSnapshotOwnershipForeignKeysAreEnforced() {
        String seal=prepareInput();
        assertThrows(DataAccessException.class,()->uniqueSeal("S"));
        assertThrows(DataAccessException.class,()->uniqueSeal("absent-snapshot"));
        String other="x"+UUID.randomUUID().toString().replace("-","");createRun(other,"OTHER");
        assertThrows(DataAccessException.class,()->input(other,"OTHER",seal,new byte[]{1}));
        assertThrows(DataAccessException.class,()->input(other,"S",seal,new byte[]{2}));
        assertThrows(DataAccessException.class,()->jdbc.update("DELETE FROM DATASET_SNAPSHOT WHERE snapshot_id='S'"));
    }
    @Test void requestIdentityIsUniqueExactAndRunInputCannotPointAtMissingSeal() {
        String seal=prepareInput();String other="x"+UUID.randomUUID().toString().replace("-","");createRun(other,"S");
        assertThrows(DataAccessException.class,()->input(other,"S",seal,run.getBytes(StandardCharsets.UTF_8)));
        assertThrows(DataAccessException.class,()->input(other,"S","missing-conversion",new byte[]{2}));
        input(other,"S",seal,(run+" ").getBytes(StandardCharsets.UTF_8));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM MINING_RUN_INPUT_BINDING WHERE request_identity=? OR request_identity=?",Integer.class,
                run.getBytes(StandardCharsets.UTF_8),(run+" ").getBytes(StandardCharsets.UTF_8)));
    }
    @Test void newExecutionConfigReusesSealAndExistingReaderKeepsNullCapabilityDisabled() {
        String seal=prepareInput();String second="x"+UUID.randomUUID().toString().replace("-","");createRun(second,"S");
        jdbc.update("UPDATE MINING_RUN SET minsup_configured=2,effective_threshold=2 WHERE run_id=?",second);
        input(second,"S",seal,second.getBytes(StandardCharsets.UTF_8));
        jdbc.update("UPDATE MINING_RUN_INPUT_BINDING SET engine_revision='other-fixture-build' WHERE run_id=?",second);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM SNAPSHOT_CONVERSION_SEAL WHERE snapshot_id='S'",Integer.class));
        pattern(run,"<(2)>",null,null);pattern(run,"<(3)>",.4,"UNCONFIRMED_REFERENCE");
        jdbc.update("UPDATE MINING_RUN SET status='SUCCEEDED',pattern_count=2 WHERE run_id=?",run);
        var page=new vn.edu.wspm.backend.services.pattern.PatternListService(new JdbcPatternListReader(mysql.dataSource())).list(run,0,20);
        assertFalse(page.weightFilter().enabled());assertEquals("MIXED_OR_MISSING_POLICY",page.weightFilter().reason());
        assertEquals("<(3)>",page.items().getFirst().canonicalPattern());assertNull(page.items().get(1).patternWeight());
        assertNull(page.items().get(1).patternWeightPolicy());
        // Fixture schema accepts updates; immutability and manifest-field consistency require the future service protocol.
    }
    void ownership(long fence,byte[] token) {
        jdbc.update("INSERT INTO MINING_RUN_OWNERSHIP(run_id,fence,attempt_token,worker_id,lease_until,updated_at) VALUES(?,?,?,'schema-worker',?,?)",run,fence,token,TIME.plusMinutes(1),TIME);
    }
    void receipt(long fence,byte[] token,long patterns,long itemRows,byte[] inputDigest) {
        byte[] bytes="{\"schemaReceiptVersion\":1}\n".getBytes(StandardCharsets.UTF_8);
        jdbc.update("INSERT INTO MINING_RUN_COMPLETION(run_id,input_digest,fence,attempt_token,receipt_version,receipt_digest,result_map_digest,persisted_payload_digest,dictionary_digest,pattern_count,pattern_item_count,dictionary_count,manifest_bytes,committed_at) VALUES(?,?,?,?,1,UNHEX(?),UNHEX(?),UNHEX(?),UNHEX(?),?,?,1,?,?)",
                run,inputDigest,fence,token,hash(bytes),"2".repeat(64),"3".repeat(64),"4".repeat(64),patterns,itemRows,bytes,TIME);
    }
    @Test void receiptReferencesExactInputAndCurrentAttemptAndAcceptsZeroPatternShape() {
        prepareInput();byte[] token=new byte[16];token[0]=1;ownership(1,token);
        var digest=jdbc.queryForObject("SELECT input_digest FROM MINING_RUN_INPUT_BINDING WHERE run_id=?",byte[].class,run);
        assertThrows(DataAccessException.class,()->receipt(2,token,0,0,digest));
        assertThrows(DataAccessException.class,()->receipt(1,token,0,0,new byte[32]));
        assertThrows(DataAccessException.class,()->receipt(1,token,0,1,digest));
        receipt(1,token,0,0,digest);
        assertThrows(DataAccessException.class,()->receipt(1,token,0,0,digest));
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE MINING_RUN_OWNERSHIP SET fence=2 WHERE run_id=?",run));
        assertThrows(DataAccessException.class,()->jdbc.update("DELETE FROM MINING_RUN_INPUT_BINDING WHERE run_id=?",run));
        assertEquals(0,jdbc.queryForObject("SELECT pattern_count FROM MINING_RUN_COMPLETION WHERE run_id=?",Integer.class,run));
        // No trigger pretends this schema-only receipt completed a publisher transaction.
        assertEquals("QUEUED",jdbc.queryForObject("SELECT status FROM MINING_RUN WHERE run_id=?",String.class,run));
    }
    @Test void ownershipBootstrapAndShapeConstraintsRejectHalfClaims() {
        prepareInput();
        assertThrows(DataAccessException.class,()->ownership(0,new byte[16]));
        jdbc.update("INSERT INTO MINING_RUN_OWNERSHIP(run_id,fence,updated_at) VALUES(?,0,?)",run,TIME);
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE MINING_RUN_OWNERSHIP SET fence=1 WHERE run_id=?",run));
    }
    @Test void storageManifestAndPerArtifactCapsRejectOversizeEvenWithCorrectHash() {
        String seal=prepareInput();byte[] oversized=new byte[262145];
        violation(3819,()->jdbc.update("UPDATE SNAPSHOT_CONVERSION_SEAL SET manifest_bytes=?,seal_digest=UNHEX(?) WHERE conversion_id=?",oversized,hash(oversized),seal));
        byte[] rawOversize=new byte[8388609];
        violation(3819,()->jdbc.update("UPDATE SNAPSHOT_CONVERSION_SEAL SET raw_bytes=?,raw_sha256=UNHEX(?),raw_size=? WHERE conversion_id=?",rawOversize,hash(rawOversize),rawOversize.length,seal));
        byte[] atLimit=new byte[8388608];
        violation(3819,()->jdbc.update("UPDATE SNAPSHOT_CONVERSION_SEAL SET raw_bytes=?,raw_sha256=UNHEX(?),raw_size=?,sequence_bytes=?,sequence_sha256=UNHEX(?),sequence_size=? WHERE conversion_id=?",
                atLimit,hash(atLimit),atLimit.length,atLimit,hash(atLimit),atLimit.length,seal));
        violation(3819,()->jdbc.update("UPDATE SNAPSHOT_CONVERSION_SEAL SET source_type='csv' WHERE conversion_id=?",seal));
    }
    static void violation(int code,org.junit.jupiter.api.function.Executable call) {
        var failure=assertThrows(DataAccessException.class,call);
        assertInstanceOf(SQLException.class,failure.getMostSpecificCause());
        assertEquals(code,((SQLException)failure.getMostSpecificCause()).getErrorCode());
    }
    static String hash(byte[] bytes){return PreparedSnapshotMiner.sha256(bytes);}
}
