package vn.edu.wspm.backend.services.run.persistence;

import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.wspm.backend.repositories.run.*;
import vn.edu.wspm.backend.repositories.run.RunResultPublicationRepository.Snapshot;
import vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission.Outcome;

/** Atomic result handoff. The caller alone acknowledges after this proxy returns from commit. */
@Service
@Slf4j
public class RunResultPublicationService {
    private final RunItemStatisticsPersistenceService preparation;
    private final PatternPersistenceRepository parents;
    private final TemporalPatternPersistenceRepository temporal;
    private final RunItemStatisticsPersistenceRepository statistics;
    private final RunResultPublicationRepository publications;
    private final PlatformTransactionManager transactions;
    public RunResultPublicationService(RunItemStatisticsPersistenceService preparation, PatternPersistenceRepository parents,
            TemporalPatternPersistenceRepository temporal, RunItemStatisticsPersistenceRepository statistics,
            RunResultPublicationRepository publications, PlatformTransactionManager transactions) {
        this.preparation=preparation; this.parents=parents; this.temporal=temporal; this.statistics=statistics;
        this.publications=publications; this.transactions=transactions;
    }
    public String publish(Outcome outcome) {
        try { return publishVerified(outcome); }
        catch (vn.edu.wspm.backend.exception.ApplicationException e) {
            log.warn("Result publication rejected; outcome retained: runId={}, code={}",outcome == null ? null : outcome.runId(),e.code());
            throw e;
        } catch (RuntimeException e) {
            var safe=new IllegalStateException("Result publication failed: "+e.getClass().getSimpleName());
            safe.setStackTrace(e.getStackTrace());
            log.error("Result publication unavailable; outcome retained: runId={}",outcome == null ? null : outcome.runId(),safe);
            throw e;
        }
    }
    private String publishVerified(Outcome outcome) {
        long started=System.nanoTime();
        var batch=preparation.prepare(outcome); // no DB transaction during artifact verification
        var tx=new TransactionTemplate(transactions);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED); tx.setTimeout(30);
        String digest=tx.execute(status -> {
            var marker=publications.marker(outcome.runId(),true);
            if ("COMMITTED".equals(marker.get("publication_status"))) {
                var stored=publications.verified(outcome.runId());
                requireExpected(stored,batch);
                return stored.digest();
            }
            if (!"NOT_PUBLISHED".equals(marker.get("publication_status"))) throw RunResultPublicationRepository.conflict();
            parents.persist(batch.temporal().parents()); temporal.persist(batch.temporal()); statistics.persist(batch);
            statistics.verify(batch);
            var stored=publications.snapshot(outcome.runId()); requireExpected(stored,batch);
            publications.commit(outcome.runId(),stored,(System.nanoTime()-started)/1_000_000);
            return stored.digest();
        });
        log.info("Result publication confirmed: runId={}, digest={}",outcome.runId(),digest);
        return digest;
    }
    private static void requireExpected(Snapshot s, RunItemStatisticsProjector.Batch b) {
        var expectedParents=b.temporal().parents().rows().stream().map(r -> {
            Map<String,Object> m=new TreeMap<>();
            m.put("canonical_pattern",r.canonical()); m.put("canonical_pattern_hash",r.hash()); m.put("support",Long.toString(r.support()));
            m.put("support_ratio",r.ratio()); m.put("weighted_support",r.weightedSupport()); m.put("items",Integer.toString(r.itemCount()));
            m.put("sets",Integer.toString(r.itemsetCount())); m.put("pattern_weight",r.weight()); m.put("pattern_weight_policy",r.policy()); return m;
        }).toList();
        var actualParents=s.patterns().stream().map(r -> {var m=new TreeMap<>(r);m.remove("id");return m;}).toList();
        var expectedTemporal=new ArrayList<Map<String,Object>>();
        b.temporal().patterns().forEach(p -> p.items().forEach(i -> expectedTemporal.add(Map.of("hash",p.parent().hash(),
                "set_order",Integer.toString(i.itemsetOrder()),"item_order",Integer.toString(i.itemOrder()),"item_id",Integer.toString(i.itemId())))));
        var expectedItems=b.rows().stream().map(r -> Map.<String,Object>of("item_id",Integer.toString(r.itemId()),"product_id",r.productId(),
                "occurrences",Long.toString(r.occurrences()),"support",Long.toString(r.support()),"item_weight",r.weight())).toList();
        var actualItems=s.items().stream().map(r -> {var m=new TreeMap<>(r);m.remove("product_name_snapshot");m.remove("sku_snapshot");return m;}).toList();
        var identity=b.temporal().parents().context().identity();
        var context=b.temporal().parents().context();
        var minimum=context.result().minimumSupport();
        var expectedConfig=new TreeMap<String,Object>();
        expectedConfig.put("algorithm",context.result().algorithm().name()); expectedConfig.put("minsup_mode",minimum.mode().name());
        expectedConfig.put("minsup_configured",minimum.configuredValue());
        expectedConfig.put("minsup_relative_fraction",minimum.relativeMinimumSupport().isPresent() ? minimum.relativeMinimumSupport().getAsDouble() : null);
        expectedConfig.put("effective_threshold",minimum.effectiveThreshold()); expectedConfig.put("max_w",context.maxWeight());
        expectedConfig.put("min_w",context.minWeight()); expectedConfig.put("mean_w",context.result().globalMeanWeight());
        var actualConfig=new TreeMap<String,Object>(); expectedConfig.keySet().forEach(k -> actualConfig.put(k,s.binding().get(k)));
        // Provenance is compared via typed canonical encoding, independent of JSON map ordering.
        var json=new tools.jackson.databind.ObjectMapper();
        Object expectedProvenance=json.readValue(json.writeValueAsString(identity.provenance()),Map.class);
        if (!ResultManifestCodec.digest(expectedParents).equals(ResultManifestCodec.digest(actualParents))
                || !ResultManifestCodec.digest(expectedConfig).equals(ResultManifestCodec.digest(actualConfig))
                || !ResultManifestCodec.digest(expectedTemporal).equals(ResultManifestCodec.digest(s.temporal()))
                || !ResultManifestCodec.digest(expectedItems).equals(ResultManifestCodec.digest(actualItems))
                || !ResultManifestCodec.digest(expectedProvenance).equals(ResultManifestCodec.digest(s.binding().get("document")))) {
            throw RunResultPublicationRepository.conflict();
        }
    }
}
