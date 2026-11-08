package vn.edu.wspm.e2e;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.config.BeanPostProcessor;
import vn.edu.wspm.backend.services.run.engine.MiningEngine;
import vn.edu.wspm.backend.services.run.engine.MiningEngineRequest;

/** Bounded transparent delegate: records actual structural input, counts calls, optionally holds first call. */
public final class EngineExecutionGate implements BeanPostProcessor {
    private final Path directory;
    private final AtomicInteger calls = new AtomicInteger();
    private final int maxCalls;
    public EngineExecutionGate(Path directory) { this(directory,2); }
    public EngineExecutionGate(Path directory,int maxCalls) { this.directory = directory.toAbsolutePath().normalize(); this.maxCalls=maxCalls; }
    @Override public Object postProcessAfterInitialization(Object bean, String name) {
        if (!(bean instanceof MiningEngine delegate)) return bean;
        return (MiningEngine) request -> {
            try {
                Files.createDirectories(directory);
                int call = calls.incrementAndGet();
                if (call > maxCalls) throw new IllegalStateException("Unexpected engine replay/capacity overflow");
                var inputFile = directory.resolve("input-" + request.algorithm() + (call>2 ? "-"+call : "") + ".spmf");
                if (Files.exists(inputFile)) inputFile = directory.resolve("input-" + request.algorithm() + "-" + call + ".spmf");
                Files.writeString(inputFile, spmf(request), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                Files.writeString(directory.resolve("CALLS"), Integer.toString(call));
                Files.writeString(directory.resolve("ENTERED"), "ready");
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(90);
                while (call == 1 && Files.exists(directory.resolve("HOLD")) && !Files.exists(directory.resolve("RELEASE"))) {
                    if (System.nanoTime() > deadline || Thread.currentThread().isInterrupted()) throw new IllegalStateException("Test latch deadline");
                    Thread.sleep(25);
                }
                var result=delegate.execute(request);
                if (result.supports().size() > 10000) throw new IllegalStateException("Tiny evidence pattern budget");
                var original=new java.util.TreeMap<String,Integer>();
                result.supports().forEach((p,s) -> original.put(p.serialize(),s));
                var text=new StringBuilder(); original.forEach((p,s) -> text.append(p).append('\t').append(s).append('\n'));
                byte[] map = text.toString().getBytes(StandardCharsets.UTF_8);
                byte[] input = spmf(request).getBytes(StandardCharsets.UTF_8);
                if (map.length + input.length > 8 * 1024 * 1024) throw new IllegalStateException("Evidence byte budget");
                EngineRunEvidenceObserver.atomic(directory.resolve("original-"+call+".map.tsv"), map);
                EngineRunEvidenceObserver.atomic(directory.resolve("original-"+call+".spmf"), input);
                var p = new java.util.Properties();
                p.setProperty("call", Integer.toString(call));
                p.setProperty("algorithm", result.algorithm().name());
                p.setProperty("mode", result.minimumSupport().mode().name());
                p.setProperty("configured.bits", EngineRunEvidenceObserver.bits(result.minimumSupport().configuredValue()));
                p.setProperty("resolved.bits", EngineRunEvidenceObserver.bits(result.minimumSupport().effectiveThreshold()));
                p.setProperty("relative.bits", result.minimumSupport().relativeMinimumSupport().isPresent()
                        ? EngineRunEvidenceObserver.bits(result.minimumSupport().relativeMinimumSupport().getAsDouble()) : "null");
                p.setProperty("mean.bits", EngineRunEvidenceObserver.bits(result.globalMeanWeight()));
                p.setProperty("sequenceCount", Integer.toString(result.minimumSupport().numberOfSequences()));
                p.setProperty("map.count", Long.toString(result.patternCount()));
                p.setProperty("map.sha256", sha(map)); p.setProperty("input.sha256", sha(input));
                var metadata = new java.io.ByteArrayOutputStream(); p.store(metadata, "Original engine capture before return; no DB reconstruction");
                EngineRunEvidenceObserver.atomic(directory.resolve("original-"+call+".properties"), metadata.toByteArray());
                return result;
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Test gate interrupted"); }
            catch (java.io.IOException failure) { throw new IllegalStateException("Test gate I/O unavailable"); }
        };
    }
    private static String sha(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static String spmf(MiningEngineRequest request) {
        var text = new StringBuilder();
        for (var record : request.database().records()) {
            for (var itemset : record.sequence().itemsets()) {
                for (var item : itemset.items()) text.append(item.id()).append(' ');
                text.append("-1 ");
            }
            text.append("-2\n");
        }
        return text.toString();
    }
}
