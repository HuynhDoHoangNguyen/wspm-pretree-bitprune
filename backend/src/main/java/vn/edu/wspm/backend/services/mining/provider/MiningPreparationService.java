package vn.edu.wspm.backend.services.mining.provider;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.sql.DataSource;
import vn.edu.wspm.backend.repositories.JdbcSnapshotInputReader;
import vn.edu.wspm.backend.services.dataset.RawSnapshotStorage;
import vn.edu.wspm.backend.services.mining.conversion.OrderSnapshotConverter;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.backend.services.mining.provider.TrustedRawSnapshotProvider.*;

/** Optional server wiring: absent/malformed configuration cannot break unrelated application startup. */
public final class MiningPreparationService {
    private final TrustedRawSnapshotProvider provider;
    private final OperatorSourceVerifier source;
    private final LoadedEngineVerifier engine;
    private final String unavailableReason;
    private MiningPreparationService(TrustedRawSnapshotProvider p,OperatorSourceVerifier s,LoadedEngineVerifier e,String reason) {
        provider=p;source=s;engine=e;unavailableReason=reason;
    }
    public static MiningPreparationService unavailable(String reason) { return new MiningPreparationService(null,null,null,reason); }
    public boolean configured() { return provider!=null; }
    public Optional<String> unavailableReason() { return Optional.ofNullable(unavailableReason); }
    public Handoff prepare(String id) {
        if(provider==null) throw new IllegalStateException(unavailableReason);
        return provider.prepare(id);
    }
    public Optional<LoadedEngineVerifier> engineVerifier() { return Optional.ofNullable(engine); }
    public Optional<OperatorSourceVerifier> sourceVerifier() { return Optional.ofNullable(source); }
    public static MiningPreparationService fromFile(String path,DataSource ds,RawSnapshotStorage storage) {
        if(path==null || path.isBlank()) return unavailable("PREPARATION_CONFIG_MISSING");
        if(ds==null || storage==null) return unavailable("PREPARATION_DEPENDENCY_MISSING");
        try {
            Path file=Path.of(path);
            if(!file.isAbsolute() || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) return unavailable("PREPARATION_CONFIG_UNREADABLE");
            var p=new Properties();
            try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) {p.load(reader);}
            return configured(p,new JdbcSnapshotInputReader(ds),storage);
        } catch(IOException | IllegalArgumentException invalid) { return unavailable("PREPARATION_CONFIG_INVALID"); }
    }
    static MiningPreparationService configured(Properties p,SnapshotInputReader reader,RawSnapshotStorage storage) {
        try {
            var limits=new OrderSnapshotConverter.Limits(Integer.parseInt(required(p,"max-raw-bytes")),
                    Integer.parseInt(required(p,"max-rows")),Integer.parseInt(required(p,"max-field-codepoints")),
                    Integer.parseInt(required(p,"max-output-bytes")));
            String mode=required(p,"minsup-mode");double value=Double.parseDouble(required(p,"minsup-value"));
            MinimumSupport support=switch(mode) {case "ABSOLUTE"->MinimumSupport.absolute(value);case "PERCENT"->MinimumSupport.percentage(value);
                default->throw new IllegalArgumentException("Unsupported minimum support mode");};
            String policy=required(p,"conversion-policy");
            if(!OrderSnapshotConverter.POLICY.equals(policy)) throw new IllegalArgumentException("Unsupported policy");
            String context=required(p,"application-context");
            if(!OperatorSourceVerifier.validText(context,80)) throw new IllegalArgumentException("Invalid application context");
            var assignments=new HashMap<String,OperatorSourceVerifier.Assignment>();
            for(String key:p.stringPropertyNames()) {
                if(key.startsWith("snapshot.") && key.endsWith(".namespace")) {
                    String id=key.substring(9,key.length()-10);
                    if(!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new IllegalArgumentException("Invalid snapshot assignment");
                    String binding=required(p,"snapshot."+id+".metadata-binding");
                    String ns=required(p,key);
                    if(!binding.matches("[0-9a-f]{64}") || !OperatorSourceVerifier.validText(ns,160)) throw new IllegalArgumentException("Invalid assignment");
                    assignments.put(id,new OperatorSourceVerifier.Assignment(binding,ns));
                }
            }
            var source=new OperatorSourceVerifier(context,assignments,storage);
            var engine=new LoadedEngineVerifier(new LoadedEngineVerifier.Expected(p.getProperty("engine-content-sha256"),p.getProperty("engine-revision")));
            var provider=new TrustedRawSnapshotProvider(reader,storage,new Configuration(limits,support,policy),source,engine);
            return new MiningPreparationService(provider,source,engine,null);
        } catch(IllegalArgumentException invalid) { return unavailable("PREPARATION_CONFIG_INVALID"); }
    }
    private static String required(Properties p,String key) {
        String value=p.getProperty(key);if(value==null || value.isBlank()) throw new IllegalArgumentException("Missing preparation configuration");return value;
    }
}
