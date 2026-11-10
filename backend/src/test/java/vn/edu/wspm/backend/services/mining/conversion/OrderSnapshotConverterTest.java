package vn.edu.wspm.backend.services.mining.conversion;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import vn.edu.wspm.backend.services.mining.PreparedSnapshotMiner;
import vn.edu.wspm.backend.services.export.OrderCsvWriter;
import vn.edu.wspm.backend.repositories.export.OrderExportRow;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.statistics.ItemStatisticsCalculator;
import vn.edu.wspm.backend.services.mining.conversion.OrderSnapshotConverter.*;

class OrderSnapshotConverterTest {
    private final OrderSnapshotConverter converter = new OrderSnapshotConverter();
    private static final Limits LIMITS = new Limits(100_000, 100, 500, 100_000);
    private static final String HEADER = "order_id,order_item_id,customer_code,ordered_at,status,product_id,"
            + "sku_snapshot,product_name_snapshot,quantity,list_unit_price,allocated_discount_amount,line_total,currency_code";
    private static final String TIME = "2026-01-01T00:00:00.000000Z";
    private static final String SPMF = "1 2 -1 1 -1 -2\n2 -1 -2\n";
    // Independently hand-authored expected bytes, not produced by the production serializer.
    private static final String MAPPING = """
            {"version":"ORDERED_AT_PRODUCT_PRESENCE_V1","items":[{"itemId":1,"productId":"p-A","nameSnapshot":"New A","skuSnapshot":"A-new","labelSourceLineId":"4","labelVariantCount":2},{"itemId":2,"productId":"p-B","nameSnapshot":"Name B","skuSnapshot":"B","labelSourceLineId":"5","labelVariantCount":1}],"sequences":[{"sid":1,"customerCode":"c-A","orders":[{"orderId":"O1","orderedAt":"2026-01-01T00:00:00.000000Z"},{"orderId":"O2","orderedAt":"2026-01-01T00:00:00.000000Z"}]},{"sid":2,"customerCode":"c-B","orders":[{"orderId":"O3","orderedAt":"2026-01-01T01:00:00.000000Z"}]}]}
            """;
    private static List<String> fixture() {
        return List.of(
                row("O1","1","c-A",TIME,"p-B","B","Name B","1","10.00","0.00","10.00"),
                row("O1","2","c-A",TIME,"p-A","A-old","Old A","1","10.00","0.00","10.00"),
                row("O1","3","c-A",TIME,"p-B","B","Name B","2","10.00","0.00","20.00"),
                row("O2","4","c-A",TIME,"p-A","A-new","New A","1","10.00","0.00","10.00"),
                row("O3","5","c-B","2026-01-01T01:00:00.000000Z","p-B","B","Name B","1","10.00","0.00","10.00"));
    }
    private static String row(String order,String line,String customer,String time,String product,
                              String sku,String name,String quantity,String price,String discount,String total) {
        return String.join(",",order,line,customer,time,"COMPLETED",product,sku,name,quantity,price,discount,total,"VND");
    }
    private static byte[] csv(List<String> rows) { return bytes(HEADER+"\r\n"+String.join("\r\n",rows)+(rows.isEmpty()?"":"\r\n")); }
    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    private static String digest(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }
    @Test void goldenBytesHashesStatisticsAndPreparedMinerCompatibility() throws Exception {
        byte[] raw=csv(fixture()); var out=converter.convert(raw,LIMITS);
        assertArrayEquals(bytes(SPMF),out.spmf()); assertArrayEquals(bytes(MAPPING),out.mapping());
        assertEquals(digest(raw),out.rawSha256()); assertEquals(digest(bytes(SPMF)),out.sequenceSha256());
        assertEquals(digest(bytes(MAPPING)),out.mappingSha256());
        var s=out.statistics();
        assertEquals(5,s.rawRowCount()); assertEquals(4,s.totalItemOccurrences());
        assertEquals(1,s.collapsedPresenceCount()); assertEquals(3,s.itemsetCount());
        assertEquals(2,s.sequenceCount()); assertEquals(2,s.distinctItemCount());
        assertEquals(1,s.singleItemsetSequenceCount()); assertEquals(1,s.multiItemsetSequenceCount());
        assertEquals(1L,s.minSequenceLength()); assertEquals(3L,s.maxSequenceLength());
        assertEquals(List.of(new ConvertedOrderSnapshot.ItemStatistic(1,2,1),
                new ConvertedOrderSnapshot.ItemStatistic(2,2,2)),s.items());
        var db=new SpmfSequenceParser().parse(new StringReader(SPMF));
        var stats=new ItemStatisticsCalculator().calculate(db);
        assertEquals(out.dictionary().keySet(),stats.itemStatistics().keySet());
        assertEquals(4,stats.totalDatabaseLength());
        var mined=new PreparedSnapshotMiner().mine(new PreparedSnapshotMiner.Input("unit-snapshot",
                out.rawSha256(),out.spmf(),out.sequenceSha256(),out.policy(),"UNIT_ONLY",
                out.dictionary(),MinimumSupport.absolute(.4)));
        assertEquals(Map.of("<(1)>",1,"<(2)>",2,"<(1 2)>",1,
                "<(1)(1)>",1,"<(2)(1)>",1,"<(1 2)(1)>",1),mined.canonicalSupports());
        assertFalse(mined.publishBlockers().isEmpty());
    }
    @Test void rowShuffleDoesNotChangeConvertedBytesButRawHashIsSeparate() {
        var original=converter.convert(csv(fixture()),LIMITS);
        for(int seed=0;seed<12;seed++) {
            var shuffled=new ArrayList<>(fixture()); Collections.shuffle(shuffled,new Random(seed));
            var changed=converter.convert(csv(shuffled),LIMITS);
            assertArrayEquals(original.spmf(),changed.spmf()); assertArrayEquals(original.mapping(),changed.mapping());
        }
        var bom=converter.convert(bytes("\uFEFF"+new String(csv(fixture()),StandardCharsets.UTF_8)),LIMITS);
        assertNotEquals(original.rawSha256(),bom.rawSha256()); assertEquals(original.mappingSha256(),bom.mappingSha256());
    }
    @Test void maxUnsignedLineAndQuantityExactMoneyAndNumericHistoricalTie() {
        String a=row("O","2","c",TIME,"P","old","old","1","9007199254740993.01","0.00","9007199254740993.01");
        String b=row("O","10","c",TIME,"P","new","new","4294967295","0.01","0.00","42949672.95");
        String c=row("O","18446744073709551615","c",TIME,"Q","Q","Q","1","0.00","0.00","0.00");
        var out=converter.convert(csv(List.of(b,c,a)),LIMITS);
        assertEquals("10",out.items().getFirst().labelSourceLineId());
        assertEquals("new",out.items().getFirst().nameSnapshot());
        assertEquals("18446744073709551615",out.items().get(1).labelSourceLineId());
        assertEquals(2,out.statistics().totalItemOccurrences());
    }
    @Test void utf8ComparatorCaseWhitespaceAndSupplementaryCharactersAreExact() {
        var products=List.of("\uD800\uDC00","\uE000","a","A"," A");
        var rows=new ArrayList<String>(); int line=0;
        for(String product:products) rows.add(row("O",Integer.toString(++line),"c",TIME,product,"sku","name","1","0.00","0.00","0.00"));
        var out=converter.convert(csv(rows),LIMITS);
        assertEquals(List.of(" A","A","a","\uE000","\uD800\uDC00"),out.items().stream().map(ConvertedOrderSnapshot.Item::productId).toList());
    }
    @Test void existingWdmFixturesAreReusedWithoutCatalogFallback() throws Exception {
        try(var input=getClass().getResourceAsStream("/wdm/csv-v1/valid.csv")) {
            assertNotNull(input); var out=converter.convert(input.readAllBytes(),LIMITS);
            assertEquals(5,out.statistics().rawRowCount()); assertEquals(3,out.statistics().itemsetCount());
            assertEquals(2,out.statistics().sequenceCount());
        }
        try(var input=getClass().getResourceAsStream("/wdm/csv-v1/business-invalid.csv")) {
            assertNotNull(input); byte[] raw=input.readAllBytes();
            assertThrows(ConversionException.class,()->converter.convert(raw,LIMITS));
        }
    }
    @Test void historicalQuotedVietnameseCommaQuoteAndNewlinesPreserved() throws Exception {
        var source=new OrderExportRow("O",BigInteger.ONE,"c",java.time.Instant.parse(TIME),"COMPLETED","P",
                "=SKU", "Laptop Việt, \"cũ\"\r\nDòng hai",1,new BigDecimal("10.00"),new BigDecimal("0.00"),new BigDecimal("10.00"),"VND");
        var raw=new OrderCsvWriter().serialize(List.of(source),10_000);
        var out=converter.convert(raw,LIMITS);
        assertEquals(source.productNameSnapshot(),out.items().getFirst().nameSnapshot());
        String mapping=new String(out.mapping(),StandardCharsets.UTF_8);
        assertTrue(mapping.contains("Laptop Việt, \\\"cũ\\\"\\u000d\\u000aDòng hai"));
        assertEquals("=SKU",out.items().getFirst().skuSnapshot());
    }
    @Test void immutableArtifactsAndCollectionsAndRawRemainUnchanged() {
        var raw=csv(fixture()); var saved=raw.clone(); var out=converter.convert(raw,LIMITS);
        assertArrayEquals(saved,raw); raw[0]=0;
        var spmf=out.spmf(); spmf[0]=0; assertArrayEquals(bytes(SPMF),out.spmf());
        var mapping=out.mapping(); mapping[0]=0; assertArrayEquals(bytes(MAPPING),out.mapping());
        assertThrows(UnsupportedOperationException.class,()->out.items().clear());
        assertThrows(UnsupportedOperationException.class,()->out.sequences().getFirst().orders().clear());
        assertThrows(UnsupportedOperationException.class,()->out.statistics().items().clear());
        assertThrows(UnsupportedOperationException.class,()->out.dictionary().clear());
        assertFalse(out.toString().contains("p-A"));
    }
    @Test void emptyHeaderHasComputedZerosNoFakeSequenceAndRawEmptyIsInvalid() {
        var out=converter.convert(bytes(HEADER+"\n"),LIMITS);
        assertEquals(ConvertedOrderSnapshot.Outcome.EMPTY,out.outcome()); assertEquals(0,out.spmf().length);
        assertEquals(0,out.statistics().sequenceCount()); assertNull(out.statistics().minSequenceLength());
        assertNull(out.statistics().maxSequenceLength()); assertTrue(out.dictionary().isEmpty());
        assertThrows(ConversionException.class,()->converter.convert(new byte[0],LIMITS));
    }
    @Test void explicitLimitsAcceptExactBoundsRejectOneLessWithoutPartialResult() {
        var raw=csv(fixture()); var result=converter.convert(raw,LIMITS);
        int largest=Math.max(result.mapping().length,result.spmf().length);
        converter.convert(raw,new Limits(raw.length,5,200,largest));
        assertCode("RAW_LIMIT_EXCEEDED",()->converter.convert(raw,new Limits(raw.length-1,5,200,largest)));
        assertCode("ROW_LIMIT_EXCEEDED",()->converter.convert(raw,new Limits(raw.length,4,200,largest)));
        assertCode("OUTPUT_LIMIT_EXCEEDED",()->converter.convert(raw,new Limits(raw.length,5,200,largest-1)));
        assertCode("FIELD_LIMIT_EXCEEDED",()->converter.convert(raw,new Limits(raw.length,5,26,largest)));
        assertThrows(IllegalArgumentException.class,()->new Limits(1,-1,1,1));
        converter.convert(bytes(HEADER),new Limits(1000,0,200,1000));
    }
    @Test void exactTextLengthBoundsCountUnicodeCodepoints() {
        var good=row("x".repeat(40),"1","c".repeat(80),TIME,"p".repeat(40),"s".repeat(80),"😀".repeat(200),"1","0.00","0.00","0.00");
        assertEquals(200,converter.convert(csv(List.of(good)),LIMITS).items().getFirst().nameSnapshot().codePointCount(0,400));
        assertThrows(ConversionException.class,()->converter.convert(csv(List.of(good.replace("p".repeat(40),"p".repeat(41)))),LIMITS));
    }
    @Test void chronologyPrecedesOrderIdAndLabelsStayPairedFromWinningRow() {
        var later=row("A","2","c", "2026-01-02T00:00:00.000000Z","P","aaa","zzz","1","0.00","0.00","0.00");
        var early=row("Z","9007199254740993","c",TIME,"P","zzz","aaa","1","0.00","0.00","0.00");
        var out=converter.convert(csv(List.of(later,early)),LIMITS);
        assertEquals(List.of("Z","A"),out.sequences().getFirst().orders().stream().map(ConvertedOrderSnapshot.Order::orderId).toList());
        assertEquals("aaa",out.items().getFirst().skuSnapshot());
        assertEquals("zzz",out.items().getFirst().nameSnapshot());
        assertEquals("2",out.items().getFirst().labelSourceLineId());
        assertArrayEquals(bytes("1 -1 1 -1 -2\n"),out.spmf());
    }
    @Test void lfNoFinalNewlineAndQuotedFieldsGiveSameConvertedArtifacts() {
        var canonical=converter.convert(csv(fixture()),LIMITS);
        String lf=HEADER+"\n"+String.join("\n",fixture());
        var changed=converter.convert(bytes(lf.replace("Name B","\"Name B\"")),LIMITS);
        assertEquals(canonical.sequenceSha256(),changed.sequenceSha256());
        assertEquals(canonical.mappingSha256(),changed.mappingSha256());
        assertNotEquals(canonical.rawSha256(),changed.rawSha256());
    }
    static Stream<String> invalidRows() {
        String good=fixture().getFirst();
        return Stream.of(
                good.replace(",1,c-A",",01,c-A"),good.replace(",1,c-A",",18446744073709551616,c-A"),
                good.replace(",1,10.00",",0,10.00"),good.replace(",1,10.00",",4294967296,10.00"),
                good.replace("COMPLETED","PENDING"),good.replace("VND","vnd"),
                good.replace("10.00","1e1"),good.replace("0.00,10.00","11.00,10.00"),
                good.replace(",10.00,VND",",9.99,VND"),good.replace(TIME,"2026-01-01T00:00:00Z"),
                good.replace(TIME,"2026-01-01T07:00:00.000000+07:00"),good.replace(TIME,"2026-02-30T00:00:00.000000Z"),
                good.replace(TIME,"2026-01-01T00:00:60.000000Z"),good.replace(TIME,"0999-01-01T00:00:00.000000Z"),
                good.replace("Name B"," "),good.replace("Name B","bad\0name"));
    }
    @ParameterizedTest @MethodSource("invalidRows") void rejectsInvalidDomainWithoutPartialOutput(String row) {
        var ex=assertThrows(ConversionException.class,()->converter.convert(csv(List.of(fixture().get(3),row)),LIMITS));
        assertEquals(3,ex.record()); assertFalse(ex.getMessage().contains(row));
    }
    @Test void duplicateAndOrderConflictEvenWithOtherwiseValidRowsAreRejected() {
        String first=fixture().getFirst();
        assertCode("DUPLICATE_LINE_ID",()->converter.convert(csv(List.of(first,first)),LIMITS));
        for(String conflict:List.of(fixture().get(1).replace("c-A","c-B"),
                fixture().get(1).replace(TIME,"2026-01-02T00:00:00.000000Z"),
                fixture().get(1).replace("VND","USD")))
            assertCode("ORDER_CONFLICT",()->converter.convert(csv(List.of(first,conflict)),LIMITS));
    }
    static Stream<String> invalidCsv() {
        String good=fixture().getFirst();
        return Stream.of("",HEADER.replace("order_id","Order_id")+"\n", "\uFEFF\uFEFF"+HEADER+"\n",
                HEADER+"\n"+good+",extra\n", HEADER+"\n"+good+"\n\n", HEADER+"\r"+good,
                HEADER+"\n\"unclosed", HEADER+"\n\"O1\"x"+good.substring(2),
                HEADER+"\nO\"1"+good.substring(2), HEADER+"\n"+good.substring(0,good.lastIndexOf(',')));
    }
    @ParameterizedTest @MethodSource("invalidCsv") void malformedCsvDoesNotSkipBadRecords(String input) {
        assertThrows(ConversionException.class,()->converter.convert(bytes(input),LIMITS));
    }
    @Test void rejectsInvalidUtf8AndDoesNotLeakRawValues() {
        var ex=assertThrows(ConversionException.class,()->converter.convert(new byte[]{(byte)0xc3,0x28},LIMITS));
        assertEquals("INVALID_UTF8",ex.code()); assertEquals(0,ex.record());
    }
    private static void assertCode(String code,org.junit.jupiter.api.function.Executable call) {
        assertEquals(code,assertThrows(ConversionException.class,call).code());
    }
}
