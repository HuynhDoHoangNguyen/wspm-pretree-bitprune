package vn.edu.wspm.backend.services.mining.provider;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import java.util.jar.*;

/** Whole JAR logical-content digest, not ZIP container bytes or a single class hash.
 * Operator/CI invokes main on the independently approved mining-core artifact. */
public final class MiningArtifactDigest {
    public static final String SCHEME="JAR_ENTRIES_SHA256_V1";
    private MiningArtifactDigest() {}
    public static String digest(JarFile jar) throws IOException {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            digest.update((SCHEME+"\n").getBytes(StandardCharsets.UTF_8));
            var entries=jar.stream().filter(e->!e.isDirectory()).sorted(Comparator.comparing(JarEntry::getName)).toList();
            var names=new HashSet<String>();
            for (var entry:entries) {
                if (!names.add(entry.getName())) throw new IOException("Duplicate JAR entry");
                var content=MessageDigest.getInstance("SHA-256"); long count=0;
                try(var input=jar.getInputStream(entry)) {
                    byte[] buffer=new byte[65536]; int read;
                    while((read=input.read(buffer))!=-1) {content.update(buffer,0,read);count=Math.addExact(count,read);}
                }
                byte[] name=entry.getName().getBytes(StandardCharsets.UTF_8);
                // DataOutputStream is explicit big-endian framing. Constant digest length = 32 bytes.
                var framing=new ByteArrayOutputStream();
                var data=new DataOutputStream(framing);
                data.writeInt(name.length);data.write(name);data.writeLong(count);data.write(content.digest());
                digest.update(framing.toByteArray());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("Supply the approved mining-core JAR path");
        try(var jar=new JarFile(Path.of(args[0]).toFile())) {
            if(jar.getJarEntry("vn/edu/wspm/mining/WspmPreTreeMiner.class")==null) throw new IOException("Not a mining-core JAR");
            System.out.println(SCHEME+"="+digest(jar));
        }
    }
}
