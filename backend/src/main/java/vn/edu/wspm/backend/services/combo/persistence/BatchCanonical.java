package vn.edu.wspm.backend.services.combo.persistence;

import java.io.*;
import java.lang.reflect.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Versioned length-framed internal encoding. Lists preserve order; maps/sets have canonical order. */
public final class BatchCanonical {
    private BatchCanonical() {}
    public static byte[] encode(Object value,int limit) {
        var output=new ByteArrayOutputStream();
        var bounded=new FilterOutputStream(output) {
            private void check(int size) {if((long)output.size()+size>limit)throw new IllegalArgumentException("PAYLOAD_LIMIT_EXCEEDED");}
            @Override public void write(int b){check(1);output.write(b);}
            @Override public void write(byte[] b,int off,int len){check(len);output.write(b,off,len);}
        };
        try {write(new DataOutputStream(bounded),value,limit);return output.toByteArray();}
        catch(IOException | ReflectiveOperationException failure){throw new IllegalArgumentException("CANONICAL_ENCODING_FAILED");}
    }
    private static void text(DataOutputStream o,String s)throws IOException {
        if(!StandardCharsets.UTF_8.newEncoder().canEncode(s))throw new IllegalArgumentException("INVALID_UNICODE");
        byte[] bytes=s.getBytes(StandardCharsets.UTF_8);o.writeInt(bytes.length);o.write(bytes);
    }
    private static void write(DataOutputStream o,Object value,int limit)throws IOException,ReflectiveOperationException {
        if(value==null){o.writeByte('N');return;}
        if(value instanceof byte[] bytes){o.writeByte('B');o.writeInt(bytes.length);o.write(bytes);return;}
        if(value instanceof String s){o.writeByte('S');text(o,s);return;}
        if(value instanceof Enum<?> e){o.writeByte('E');text(o,e.name());return;}
        if(value instanceof Boolean b){o.writeByte(b?'T':'F');return;}
        if(value instanceof BigDecimal d){o.writeByte('D');text(o,d.stripTrailingZeros().toPlainString());return;}
        if(value instanceof Double d){if(!Double.isFinite(d))throw new IllegalArgumentException("NONFINITE_METRIC");o.writeByte('R');text(o,Double.toHexString(d==0?0:d));return;}
        if(value instanceof Number n){o.writeByte('I');text(o,n.toString());return;}
        if(value instanceof Instant time){o.writeByte('Z');text(o,time.toString());return;}
        if(value instanceof java.sql.Timestamp time){write(o,time.toLocalDateTime(),limit);return;}
        if(value instanceof LocalDateTime time){o.writeByte('Z');text(o,time.toInstant(ZoneOffset.UTC).toString());return;}
        if(value instanceof Map<?,?> map){
            o.writeByte('M');o.writeInt(map.size());
            var entries=new ArrayList<>(map.entrySet());entries.sort((a,b)->Arrays.compareUnsigned(encode(a.getKey(),limit),encode(b.getKey(),limit)));
            for(var entry:entries){write(o,entry.getKey(),limit);write(o,entry.getValue(),limit);}return;
        }
        if(value instanceof Collection<?> values){
            o.writeByte('L');o.writeInt(values.size());var list=new ArrayList<>(values);
            if(value instanceof Set<?>)list.sort((a,b)->Arrays.compareUnsigned(encode(a,limit),encode(b,limit)));
            for(var item:list)write(o,item,limit);return;
        }
        if(value.getClass().isRecord()){
            o.writeByte('O');text(o,value.getClass().getName());
            var components=Arrays.stream(value.getClass().getRecordComponents()).filter(c->
                    !(c.getName().equals("createdAt")&&(value instanceof DraftBatchWrite.BatchRow||value instanceof DraftBatchWrite.ComboRow))
                    && !(c.getName().equals("writeIdentity")&&value instanceof DraftBatchWrite.Plan)).toList();
            o.writeInt(components.size());for(var c:components){text(o,c.getName());write(o,c.getAccessor().invoke(value),limit);}return;
        }
        throw new IllegalArgumentException("UNSUPPORTED_CANONICAL_TYPE");
    }
}
