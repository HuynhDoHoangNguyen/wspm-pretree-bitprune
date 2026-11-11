package vn.edu.wspm.backend.services.combo.generation;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.repositories.run.RunResultPublicationRepository;
import vn.edu.wspm.backend.services.combo.evaluation.CandidateEvaluation.*;
import vn.edu.wspm.backend.services.combo.grouping.*;
import vn.edu.wspm.backend.services.combo.ranking.*;
import vn.edu.wspm.backend.services.combo.persistence.*;
import vn.edu.wspm.backend.services.run.persistence.PatternWeightPolicy;
import vn.edu.wspm.backend.web.auth.AdminPrincipal;
import static vn.edu.wspm.backend.services.combo.persistence.DraftBatchWrite.*;

/** Native orchestration. Pure preview, signed immutable context, existing planner/writer semantics. */
@Service
public class NativeComboGeneration {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(NativeComboGeneration.class);
    public record Context(String version,String identity,String run,String actor,String anchor,int k,Instant at,
            String publication,String policyBinding,Map<String,CatalogProduct> catalog,ProductSelection selection,CategorySelection categorySelection,Integer recommendationK,Integer draftLimit) {
        public Context(String version,String identity,String run,String actor,String anchor,int k,Instant at,String publication,String policyBinding,Map<String,CatalogProduct> catalog,ProductSelection selection,CategorySelection categorySelection,Integer recommendationK){
            this(version,identity,run,actor,anchor,k,at,publication,policyBinding,catalog,selection,categorySelection,recommendationK,null);
        }
        public Context(String version,String identity,String run,String actor,String anchor,int k,Instant at,String publication,String policyBinding,Map<String,CatalogProduct> catalog,ProductSelection selection,CategorySelection categorySelection){
            this(version,identity,run,actor,anchor,k,at,publication,policyBinding,catalog,selection,categorySelection,null);
        }
        /** Old signed contexts keep their original persisted cap (flexible=5, legacy=preview K). */
        public int resolvedRecommendationK(){return recommendationK!=null?recommendationK:ProductSelection.flexiblePolicy(version)?5:k;}
        public Context(String version,String identity,String run,String actor,String anchor,int k,Instant at,String publication,String policyBinding,Map<String,CatalogProduct> catalog){this(version,identity,run,actor,anchor,k,at,publication,policyBinding,catalog,null,null);}
        public Context(String version,String identity,String run,String actor,String anchor,int k,Instant at,String publication,String policyBinding,Map<String,CatalogProduct> catalog,ProductSelection selection){this(version,identity,run,actor,anchor,k,at,publication,policyBinding,catalog,selection,null);}
        public Context{catalog=Map.copyOf(catalog);}
        @Override public String toString(){return "NativeGenerationContext[redacted]";}
    }
    public record Anchor(String productId,String name){}
    public record Preview(String status,List<String> issues,int examined,int accepted,int rejected,int groups,
            int eligible,List<String> topPatternIds,List<Anchor> anchors,Map<String,Long> reasonCounts,Map<String,String> referencePrices,String contextToken,
            String policyVersion,List<Proposal> proposals,int plannedGroups){
        public Preview(String status,List<String> issues,int examined,int accepted,int rejected,int groups,int eligible,List<String> top,List<Anchor> anchors,Map<String,Long> reasons,Map<String,String> prices,String token,String policyVersion,List<Proposal> proposals){this(status,issues,examined,accepted,rejected,groups,eligible,top,anchors,reasons,prices,token,policyVersion,proposals,groups);}
        public Preview(String status,List<String> issues,int examined,int accepted,int rejected,int groups,int eligible,List<String> top,List<Anchor> anchors,Map<String,Long> reasons,Map<String,String> prices,String token){this(status,issues,examined,accepted,rejected,groups,eligible,top,anchors,reasons,prices,token,NativeComboPolicy.VERSION,List.of());}
    }
    public record ProductOption(long itemId,String productId,String name,String sku){}
    public record Options(String runId,String policyVersion,int maxDistinctProducts,long maxQuantity,int maxBatchItems,List<ProductOption> products){}
    public record CategoryOption(String categoryId,String name,int productCount){}
    public record CategoryOptions(String runId,String policyVersion,int maxDistinctProducts,long maxQuantity,int maxBatchItems,List<CategoryOption> categories){}
    public record ProposalItem(long itemsetOrder,long itemOrder,long itemId,String productId,String name,String sku,long quantity,boolean required,String unitPrice,String lineTotal){}
    public record Proposal(String sourcePatternId,String canonicalPattern,String comboKind,String total,List<ProposalItem> items){}
    public record Outcome(String status,List<String> issues,String batchId,int storedGroups){}
    private record Compiled(Preview preview,Request request,Context context){}
    private final PublishedComboInputService inputs;
    private final NativeComboPolicy policy;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final RunResultPublicationRepository nativePublication;
    private final TransactionTemplate reads;
    private final JdbcDraftBatchStore store;
    private final vn.edu.wspm.backend.services.run.query.RunResultQueryService transactionalResults;
    private final byte[] key;
    private final vn.edu.wspm.backend.services.run.query.RunResultQueryService results;
    public NativeComboGeneration(PublishedComboInputService inputs,NativeComboPolicy policy,ObjectMapper json,
            DataSource ds,@Value("${tv2.combo.context-key:${auth.jwt.secret:}}") String secret,vn.edu.wspm.backend.services.run.query.RunResultQueryService results) {
        this.results=results;
        this.inputs=inputs;this.policy=policy;this.json=json;jdbc=new JdbcTemplate(ds);
        // Direct transaction-aware repository uses the SAME DataSource-bound JDBC connection as writer/review.
        // Calling proxied JPA read service from a DataSourceTransactionManager would not preserve that contract.
        nativePublication=new RunResultPublicationRepository(new NamedParameterJdbcTemplate(ds),json);
        transactionalResults=new vn.edu.wspm.backend.services.run.query.RunResultQueryService(
                new vn.edu.wspm.backend.repositories.run.RunResultQueryRepository(new NamedParameterJdbcTemplate(ds)),json,16777216);
        transactionalResults.publications(nativePublication);
        reads=new TransactionTemplate(new DataSourceTransactionManager(ds));reads.setReadOnly(true);
        reads.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        key=secret.getBytes(StandardCharsets.UTF_8);
        store=new JdbcDraftBatchStore(ds,new JdbcDraftBatchStore.Limits(1000,4000,10000,100000,1048576,1048576,2097152),this::binding);
    }
    public JdbcDraftBatchStore store(){return store;}
    public boolean configured(){return key.length>=32;}
    private String policyBinding(String version){return ProductSelection.flexiblePolicy(version)?hash(BatchCanonical.encode(List.of(version,CategorySelection.VERSION.equals(version)?CategorySelection.SOURCE:ProductSelection.SOURCE,ProductSelection.MAX_PRODUCTS,ProductSelection.MAX_QUANTITY,PatternWeightPolicy.VERSION),65536)):policyBinding();}
    public CategoryOptions categoryOptions(String run,AdminPrincipal principal){
        outsideTransaction();reads.execute(s->{actor(principal.userId(),false);return null;});var prepared=inputs.prepare(run);
        var categories=reads.execute(s->{
            var catalog=catalog(prepared.dictionary().stream().map(DictionaryItem::productId).toList(),false);
            var counts=new TreeMap<String,Integer>();catalog.values().stream().filter(p->"ACTIVE".equals(p.productStatus())&&"ACTIVE".equals(p.categoryStatus())).forEach(p->counts.merge(p.categoryId(),1,Integer::sum));
            var list=new ArrayList<CategoryOption>();
            counts.forEach((id,count)->{var rows=jdbc.queryForList("SELECT category_id,name FROM CATEGORY WHERE category_id=?",id);
                if(rows.size()!=1||!id.equals(rows.getFirst().get("category_id")))throw invalid("CATEGORY_IDENTITY_INVALID");
                list.add(new CategoryOption(id,rows.getFirst().get("name").toString(),count));});return List.copyOf(list);
        });
        return new CategoryOptions(run,CategorySelection.VERSION,ProductSelection.MAX_PRODUCTS,ProductSelection.MAX_QUANTITY,store.limits().maxItems(),categories);
    }
    public Options options(String run,AdminPrincipal principal){
        outsideTransaction();reads.execute(s->{actor(principal.userId(),false);return null;});
        var prepared=inputs.prepare(run);
        return new Options(run,ProductSelection.VERSION,ProductSelection.MAX_PRODUCTS,ProductSelection.MAX_QUANTITY,store.limits().maxItems(),
                prepared.dictionary().stream().sorted(Comparator.comparingLong(DictionaryItem::itemId)).map(d->new ProductOption(d.itemId(),d.productId(),d.nameSnapshot(),d.skuSnapshot())).toList());
    }
    public String policyBinding(){return hash(BatchCanonical.encode(List.of(NativeComboPolicy.VERSION,
            new TreeSet<>(policy.laptops),new TreeSet<>(policy.accessories),PatternWeightPolicy.VERSION),65536));}
    public JdbcDraftBatchStore.MiningBinding binding(String run,boolean lock){
        if(lock)nativePublication.marker(run,true);
        transactionalResults.publishedBundle(run); // full semantic verification on SAME transaction/connection
        var verified=nativePublication.verified(run);
        return new JdbcDraftBatchStore.MiningBinding(verified.digest(),verified.digest());
    }
    public Map<String,CatalogProduct> catalog(Collection<String> ids,boolean lock){
        if(ids.size()>10000)throw invalid("CATALOG_LIMIT_EXCEEDED");
        var products=new TreeMap<String,CatalogProduct>();
        for(String id:new TreeSet<>(ids)){
            var rows=jdbc.queryForList("SELECT p.product_id,p.category_id,p.status,c.status cs,p.current_price,p.currency_code,p.version FROM PRODUCT p JOIN CATEGORY c ON c.category_id=p.category_id WHERE p.product_id=?"+(lock?" FOR SHARE":""),id);
            if(rows.size()!=1||!id.equals(rows.getFirst().get("product_id")))continue;
            var r=rows.getFirst();products.put(id,new CatalogProduct(id,r.get("category_id").toString(),r.get("status").toString(),r.get("cs").toString(),
                    (BigDecimal)r.get("current_price"),r.get("currency_code").toString(),r.get("version").toString(),Inventory.UNKNOWN,null));
        }
        return Map.copyOf(products);
    }
    private void actor(String id,boolean lock){
        String suffix=lock?" FOR SHARE":"";
        var accounts=jdbc.queryForList("SELECT user_id,status FROM USER_ACCOUNT WHERE user_id=?"+suffix,id);
        var roles=jdbc.queryForList("SELECT r.code FROM USER_ROLE u JOIN ROLE r ON r.role_id=u.role_id WHERE u.user_id=?"+suffix,id);
        if(accounts.size()!=1||!id.equals(accounts.getFirst().get("user_id"))||!"ACTIVE".equals(accounts.getFirst().get("status"))||roles.stream().noneMatch(r->"ADMIN".equals(r.get("code"))))throw new ApplicationException(ErrorCode.ACCESS_DENIED);
    }
    public Preview preview(String run,String anchor,Integer k,String identity,AdminPrincipal principal){
        return preview(run,anchor,k,identity,principal,null);
    }
    public Preview preview(String run,String anchor,Integer k,String identity,AdminPrincipal principal,ProductSelection selection){
        return preview(run,anchor,k,identity,principal,selection,null,null);
    }
    public Preview previewCategories(String run,String anchor,Integer k,String identity,AdminPrincipal principal,CategorySelection selection){
        return previewCategories(run,anchor,k,identity,principal,selection,null);
    }
    public Preview previewSelected(String run,String anchor,Integer k,String identity,AdminPrincipal principal,ProductSelection selection,Integer recommendationK){
        return previewSelected(run,anchor,k,identity,principal,selection,recommendationK,null);
    }
    public Preview previewCategories(String run,String anchor,Integer k,String identity,AdminPrincipal principal,CategorySelection selection,Integer recommendationK){
        return previewCategories(run,anchor,k,identity,principal,selection,recommendationK,null);
    }
    private Preview preview(String run,String anchor,Integer k,String identity,AdminPrincipal principal,ProductSelection selection,CategorySelection categorySelection,Integer recommendationK){
        return preview(run,anchor,k,identity,principal,selection,categorySelection,recommendationK,null);
    }
    public Preview previewSelected(String run,String anchor,Integer k,String identity,AdminPrincipal principal,ProductSelection selection,Integer recommendationK,Integer draftLimit){
        return preview(run,anchor,k,identity,principal,Objects.requireNonNull(selection),null,recommendationK,draftLimit);
    }
    public Preview previewCategories(String run,String anchor,Integer k,String identity,AdminPrincipal principal,CategorySelection selection,Integer recommendationK,Integer draftLimit){
        return preview(run,anchor,k,identity,principal,null,Objects.requireNonNull(selection),recommendationK,draftLimit);
    }
    private Preview preview(String run,String anchor,Integer k,String identity,AdminPrincipal principal,ProductSelection selection,CategorySelection categorySelection,Integer recommendationK,Integer draftLimit){
        outsideTransaction();
        if(!configured())return new Preview("BLOCKED",List.of("CATEGORY_OR_SIGNING_CONFIG_MISSING"),0,0,0,0,0,List.of(),List.of(),Map.of(),Map.of(),null);
        validate(run,anchor,k,identity);
        if(recommendationK!=null&&(recommendationK<1||recommendationK>20))throw invalid("INVALID_RECOMMENDATION_K");
        if(draftLimit!=null&&(draftLimit<1||draftLimit>1000))throw invalid("INVALID_DRAFT_LIMIT");
        reads.execute(s->{actor(principal.userId(),false);return null;});
        var meta=results.metadata(run);
        if(meta.publishedReadable()&&"0".equals(meta.storedPatternCount()))return new Preview("EMPTY",List.of("NO_PATTERNS"),0,0,0,0,0,List.of(),List.of(),Map.of(),Map.of(),null);
        var prepared=inputs.prepare(run);
        var saved=reads.execute(s->jdbc.queryForList("SELECT CASE WHEN OCTET_LENGTH(resolved_context)<=524288 THEN resolved_context END context FROM COMBO_BATCH_WRITE_EVIDENCE WHERE request_identity=?",identity.getBytes(StandardCharsets.UTF_8)));
        if(!saved.isEmpty()){
            var bytes=saved.getFirst().get("context");if(!(bytes instanceof byte[] b))throw invalid("SAVED_GENERATION_CONTEXT_INVALID");
            var frozen=decode(new String(b,StandardCharsets.UTF_8));
            if(!Objects.equals(frozen.draftLimit,draftLimit))throw invalid("WRITE_IDENTITY_CONTENT_CONFLICT");
            if(!frozen.run.equals(run)||!frozen.actor.equals(principal.userId())||!ProductSelection.flexiblePolicy(frozen.version)&&(!Objects.equals(frozen.anchor,anchor)||frozen.k!=(k==null?5:k))||!Objects.equals(frozen.selection,selection)||!Objects.equals(frozen.categorySelection,categorySelection)||ProductSelection.flexiblePolicy(frozen.version)&&frozen.resolvedRecommendationK()!=(recommendationK==null?5:recommendationK))throw invalid("WRITE_IDENTITY_CONTENT_CONFLICT");
            var display=!ProductSelection.flexiblePolicy(frozen.version)?frozen:new Context(frozen.version,frozen.identity,frozen.run,frozen.actor,anchor,k==null?5:k,frozen.at,frozen.publication,frozen.policyBinding,frozen.catalog,frozen.selection,frozen.categorySelection,frozen.recommendationK,frozen.draftLimit);
            var p=compile(prepared,display).preview;
            return new Preview(p.status,p.issues,p.examined,p.accepted,p.rejected,p.groups,p.eligible,p.topPatternIds,p.anchors,p.reasonCounts,p.referencePrices,"READY".equals(p.status)?new String(b,StandardCharsets.UTF_8):null,p.policyVersion,p.proposals,p.plannedGroups);
        }
        var catalog=reads.execute(s->{actor(principal.userId(),false);return catalog(prepared.dictionary().stream().map(DictionaryItem::productId).toList(),false);});
        String version=categorySelection!=null?CategorySelection.VERSION:selection==null?NativeComboPolicy.VERSION:ProductSelection.VERSION;
        var c=new Context(version,identity,run,principal.userId(),anchor,k==null?5:k,
                Instant.now().truncatedTo(ChronoUnit.MICROS),prepared.metadata().resultSha256(),policyBinding(version),catalog,selection,categorySelection,(selection!=null||categorySelection!=null)?(recommendationK==null?5:recommendationK):null,draftLimit);
        return compile(prepared,c).preview;
    }
    private Compiled compile(PublishedComboInputService.Prepared prepared,Context c){
        if(!c.publication.equals(prepared.metadata().resultSha256())||!c.policyBinding.equals(policyBinding(c.version)))throw invalid("GENERATION_CONTEXT_CHANGED");
        var quantities=new TreeMap<String,Long>();
        if(c.selection!=null){
            var byItem=new HashMap<Long,DictionaryItem>();prepared.dictionary().forEach(d->byItem.put(d.itemId(),d));
            for(var item:c.selection.requiredItems()){
                var d=byItem.get(item.itemId());if(d==null||quantities.put(d.productId(),item.quantity())!=null)throw invalid("SELECTED_ITEM_NOT_IN_RUN");
            }
        }
        var requiredCategories=new TreeSet<String>();
        if(c.categorySelection!=null){
            for(var category:c.categorySelection.requiredCategories()){
                boolean available=c.catalog.values().stream().anyMatch(p->category.categoryId().equals(p.categoryId())&&"ACTIVE".equals(p.categoryStatus())&&"ACTIVE".equals(p.productStatus()));
                if(!available)throw invalid("SELECTED_CATEGORY_NOT_IN_RUN");
                requiredCategories.add(category.categoryId());
                c.catalog.values().stream().filter(p->category.categoryId().equals(p.categoryId())).forEach(p->quantities.put(p.productId(),category.quantity()));
            }
        }
        boolean flexible=ProductSelection.flexiblePolicy(c.version);
        int min=c.categorySelection!=null?c.categorySelection.minDistinctProducts():c.selection!=null?c.selection.minDistinctProducts():2;
        int max=c.categorySelection!=null?c.categorySelection.maxDistinctProducts():c.selection!=null?c.selection.maxDistinctProducts():4;
        String source=c.categorySelection!=null?c.categorySelection.policyReference():c.selection==null?NativeComboPolicy.SOURCE:c.selection.policyReference();
        var evaluation=!flexible?policy.evaluation():new Policy(c.version,source,Set.of(),Set.of(),EligibilityMode.CATALOG_ONLY,
                new SelectionRules(c.categorySelection==null?quantities.keySet():Set.of(),min,max,requiredCategories));
        if(prepared.patterns().stream().anyMatch(p->!PatternWeightPolicy.VERSION.equals(p.patternWeightPolicy())))throw invalid("UNSUPPORTED_WEIGHT_POLICY");
        var session=inputs.evaluate(prepared,new PublishedComboInputService.BusinessContext(Purpose.DEMO_CATALOG,source,
                evaluation,c.catalog,"CATALOG:"+hash(BatchCanonical.encode(c.catalog,262144)),c.at));
        var reasons=new TreeMap<String,Long>();session.patterns().forEach(p->p.reasons().forEach(r->reasons.merge(r.code(),1L,Long::sum)));
        var context=new CandidateGrouping.Context(c.run,prepared.metadata().provenance().snapshotId(),c.version,
                source,EligibilityMode.CATALOG_ONLY,DraftBatchAuthority.nativeReference(c.run,c.publication),
                "CATALOG:"+hash(BatchCanonical.encode(c.catalog,262144)),c.at);
        var grouped=new CandidateGrouper().group(session,context,policy.ranking(c.k).weightPolicy());
        if(!grouped.completed())throw invalid("CANDIDATE_CONTEXT_INVALID");
        var anchorMap=new TreeMap<String,String>();
        for(var g:grouped.groups())for(var set:g.itemsets())if(c.categorySelection!=null||g.comboKind()==CandidateGrouping.ComboKind.SAME_ORDER_BUNDLE||set==g.itemsets().getFirst())
            for(var i:set.items())anchorMap.put(i.productId(),i.nameSnapshot());
        var anchors=anchorMap.entrySet().stream().map(e->new Anchor(e.getKey(),e.getValue())).toList();
        var ranked=new ComboPreviewRanker().preview(grouped,context,c.anchor,policy.ranking(c.k));
        String status=grouped.groups().isEmpty()?"EMPTY":!flexible&&(c.anchor==null||c.anchor.isBlank())?"SELECT_ANCHOR":ranked.completed()?"READY":"BLOCKED";
        var issues=ranked.completed()?List.<String>of():ranked.errors();
        if("SELECT_ANCHOR".equals(status))issues=List.of();
        if(c.anchor!=null&&!c.anchor.isBlank()&&!anchorMap.containsKey(c.anchor)){
            status="BLOCKED";issues=List.of("INVALID_ANCHOR_MEMBERSHIP");
        }
        var fields=new HashMap<CandidateGrouping.StructureKey,Fields>();
        for(var g:grouped.groups()){
            var values=new HashMap<Position,ItemValues>();BigDecimal price=BigDecimal.ZERO;
            for(var set:g.itemsets())for(var i:set.items()){
                var p=i.catalogEvidence();if(!"VND".equals(p.currency())||p.price().scale()>2)throw invalid("VND_PRICE_REQUIRED");
                long quantity=quantities.getOrDefault(i.productId(),1L);
                price=price.add(p.price().multiply(BigDecimal.valueOf(quantity)));
                if(price.compareTo(new BigDecimal("99999999999999999.99"))>0)throw invalid("COMBO_TOTAL_OUT_OF_RANGE");
                values.put(new Position(set.itemsetOrder(),i.itemOrder()),new ItemValues(i.productId(),quantity,p.price(),context.catalogSnapshotRef(),false));
            }
            String names=g.itemsets().stream().flatMap(s->s.items().stream()).map(i->i.nameSnapshot()).collect(java.util.stream.Collectors.joining(" + "));
            fields.put(g.structureKey(),new Fields(stable("combo",c.identity+":"+g.structureKey().serialized()),truncate(names,255),
                    "Suggested "+g.comboKind()+" from Pattern "+g.primary().patternId()+"; observed sequence, not a causal claim.","VND",price,price,context.catalogSnapshotRef(),"NONE",values));
        }
        // Only the signed draft limit truncates persistence. Null preserves historical batches.
        var selected=selectDraftGroups(grouped,c.draftLimit);
        var writeFields=new HashMap<CandidateGrouping.StructureKey,Fields>();
        selected.groups().forEach(g->writeFields.put(g.structureKey(),fields.get(g.structureKey())));
        var writeRanking=!flexible?ranked:new ComboPreviewRanker().preview(selected,context,null,policy.ranking(c.resolvedRecommendationK()));
        var request=new Request(selected,context,writeRanking,c.identity,stable("batch",c.identity),c.actor,c.at,writeFields,
                !flexible?null:new Bounds(min,max));
        if("READY".equals(status)){
            var validation=new DraftBatchPlanner().plan(request);
            if(!validation.valid()){status="BLOCKED";issues=validation.errors();}
            if(selected.groups().size()>1000){status="BLOCKED";issues=List.of("GENERATION_RESOURCE_LIMIT_EXCEEDED");}
            if(validation.valid()&&"READY".equals(status)) {
                try {
                    byte[] content=NativeComboContent.encode(selected,validation.plan(),store.limits().maxContentBytes());
                    var auth=authorization(c,validation.plan(),hash(content),sign(c));
                    byte[] evidence=BatchCanonical.encode(Map.of("version","COMBO_DRAFT_EVIDENCE_V1","runId",c.run,
                            "miningReference",context.publishedEvidenceRef(),"authorization",auth),store.limits().maxEvidenceBytes());
                    byte[] representation=store.plannedRepresentation(validation.plan());
                    if((long)content.length+evidence.length+representation.length>4L*1024*1024)throw new IllegalArgumentException("PAYLOAD_LIMIT_EXCEEDED");
                } catch(IllegalArgumentException invalid) {
                    status="BLOCKED";issues=List.of("PAYLOAD_LIMIT_EXCEEDED".equals(invalid.getMessage())?"GENERATION_RESOURCE_LIMIT_EXCEEDED":"PAYLOAD_OR_EVIDENCE_INVALID");
                }
            }
        }
        var prices=new TreeMap<String,String>();
        for(var group:ranked.items())prices.put(group.group().primary().patternId(),fields.get(group.group().structureKey()).referenceOriginalPrice().toPlainString());
        String token="READY".equals(status)?sign(c):null;
        var proposals=new ArrayList<Proposal>();
        if(flexible&&selected.groups().size()<=store.limits().maxGroups()){
            int itemCount=0;
            for(var rankedGroup:ranked.items()){
                var g=rankedGroup.group();var items=new ArrayList<ProposalItem>();
                for(var set:g.itemsets())for(var i:set.items()){
                    if(++itemCount>store.limits().maxItems())throw invalid("PREVIEW_ITEM_LIMIT_EXCEEDED");
                    long q=quantities.getOrDefault(i.productId(),1L);var price=i.catalogEvidence().price();
                    items.add(new ProposalItem(set.itemsetOrder(),i.itemOrder(),i.sourceItemId(),i.productId(),i.nameSnapshot(),i.skuSnapshot(),q,quantities.containsKey(i.productId()),price.toPlainString(),price.multiply(BigDecimal.valueOf(q)).toPlainString()));
                }
                proposals.add(new Proposal(g.primary().patternId(),g.primary().source().canonicalPattern(),g.comboKind().name(),fields.get(g.structureKey()).referenceOriginalPrice().toPlainString(),List.copyOf(items)));
            }
        }
        return new Compiled(new Preview(status,issues,session.examined(),session.accepted(),session.rejected(),grouped.groups().size(),
                ranked.eligibleCount(),ranked.items().stream().map(i->i.group().primary().patternId()).toList(),anchors,Map.copyOf(reasons),Map.copyOf(prices),token,c.version,List.copyOf(proposals),selected.groups().size()),request,c);
    }
    static CandidateGrouping.Result selectDraftGroups(CandidateGrouping.Result grouped,Integer limit){
        if(limit==null)return grouped;
        if(limit<1||limit>1000)throw invalid("INVALID_DRAFT_LIMIT");
        return new CandidateGrouping.Result(grouped.completed(),grouped.errors(),grouped.groups().stream()
                .sorted((a,b)->CandidateGrouper.primaryOrder().compare(a.primary(),b.primary())).limit(limit).toList());
    }
    public Outcome submit(String token,AdminPrincipal principal,boolean reconcile){
        outsideTransaction();
        if(!configured())return new Outcome("BLOCKED",List.of("CATEGORY_OR_SIGNING_CONFIG_MISSING"),null,0);
        Context c=decode(token);if(!c.actor.equals(principal.userId()))throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        reads.execute(s->{actor(c.actor,false);return null;});
        var compiled=compile(inputs.prepare(c.run),c);
        if(!"READY".equals(compiled.preview.status))return new Outcome(compiled.preview.status,compiled.preview.issues,null,0);
        // Verify only new writes against current catalog. Retry/reconcile must retain the frozen original context.
        var writer=new DraftBatchWriter(store,(r,p,d)->new DraftBatchAuthority.Decision(List.of(),authorization(c,p,d,token)),Clock.fixed(c.at,ZoneOffset.UTC));
        var outcome=reconcile?writer.reconcile(compiled.request):writer.write(compiled.request);
        log.info("Native Combo command completed: status={}, eligibleGroups={}, plannedGroups={}",outcome.status(),compiled.preview.groups,compiled.preview.plannedGroups);
        return new Outcome(outcome.status(),outcome.errors(),outcome.receipt()==null?null:outcome.receipt().batchId(),outcome.receipt()==null?0:outcome.receipt().comboIds().size());
    }
    public Context decode(String token){
        try{
            if(token==null||token.length()>524288)throw invalid("INVALID_PREVIEW_CONTEXT");String[] parts=token.split("\\.",-1);
            if(parts.length!=2)throw invalid("INVALID_PREVIEW_CONTEXT");byte[] bytes=Base64.getUrlDecoder().decode(parts[0]);
            if(!java.security.MessageDigest.isEqual(mac(bytes),Base64.getUrlDecoder().decode(parts[1])))throw invalid("INVALID_PREVIEW_CONTEXT");
            var c=json.readValue(bytes,Context.class);
            if(!(NativeComboPolicy.VERSION.equals(c.version)&&c.selection==null&&c.categorySelection==null||ProductSelection.VERSION.equals(c.version)&&c.selection!=null&&c.categorySelection==null||CategorySelection.VERSION.equals(c.version)&&c.categorySelection!=null&&c.selection==null)
                    ||!policyBinding(c.version).equals(c.policyBinding)||c.catalog.size()>10000
                    ||c.resolvedRecommendationK()<1||c.resolvedRecommendationK()>20
                    ||c.draftLimit!=null&&(c.draftLimit<1||c.draftLimit>1000||!ProductSelection.flexiblePolicy(c.version)))throw invalid("POLICY_CONTEXT_CHANGED");
            return c;
        }catch(RuntimeException invalid){throw invalid("INVALID_PREVIEW_CONTEXT");}
    }
    private DraftBatchAuthority.Authorization authorization(Context c,Plan p,String digest,String token){
        var approvals=new DraftBatchAuthority.Approvals(c.version,"PRODUCT_ITEMSETS_LENGTH_V1",p.context().policyConfirmationRef(),
                NativeComboPolicy.SOURCE,NativeComboPolicy.SOURCE,NativeComboPolicy.SOURCE,c.categorySelection!=null?CategorySelection.SOURCE:c.selection==null?NativeComboPolicy.SOURCE:ProductSelection.SOURCE,p.context().catalogSnapshotRef());
        return new DraftBatchAuthority.Authorization(digest,c.publication,c.publication,approvals,token.getBytes(StandardCharsets.UTF_8));
    }
    /** Called only under existing review transaction. Reproduce issued context and every stored row. */
    public vn.edu.wspm.backend.services.combo.review.ComboReviewAuthority.Check review(
            vn.edu.wspm.backend.repositories.ComboAdminReader.Bundle current){
        var confirmed=new vn.edu.wspm.backend.services.combo.review.ComboReview.Policy(configured()?NativeComboPolicy.SOURCE:null);
        try{
            if(!configured()||!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw invalid("REVIEW_TRANSACTION_REQUIRED");
            String batch=current.row().batchId();
            var size=jdbc.queryForMap("SELECT OCTET_LENGTH(resolved_context) n,OCTET_LENGTH(content_bytes) c,OCTET_LENGTH(evidence_bytes) e,OCTET_LENGTH(expected_representation) r FROM COMBO_BATCH_WRITE_EVIDENCE WHERE batch_id=?",batch);
            for(String field:List.of("n","c","e","r"))if(!(size.get(field) instanceof Number n)||n.longValue()<1||n.longValue()>2097152)throw invalid("REVIEW_SIZE_INVALID");
            var saved=jdbc.queryForMap("SELECT * FROM COMBO_BATCH_WRITE_EVIDENCE WHERE batch_id=?",batch);
            Context c=decode(new String((byte[])saved.get("resolved_context"),StandardCharsets.UTF_8));
            confirmed=new vn.edu.wspm.backend.services.combo.review.ComboReview.Policy(c.categorySelection!=null?CategorySelection.SOURCE:c.selection==null?NativeComboPolicy.SOURCE:ProductSelection.SOURCE);
            if(!current.row().runId().equals(c.run)||!stable("batch",c.identity).equals(batch)||!"DRAFT".equals(current.row().batchStatus()))throw invalid("BATCH_OWNERSHIP_INVALID");
            var prepared=new PublishedComboInputService.Prepared(transactionalResults.publishedBundle(c.run));
            var compiled=compile(prepared,c);var planned=new DraftBatchPlanner().plan(compiled.request);
            if(!planned.valid())throw invalid("GENERATION_REPRODUCTION_FAILED");var plan=planned.plan();
            var ordered=new CandidateGrouping.Result(true,List.of(),compiled.request.grouping().groups().stream().sorted(Comparator.comparing(g->g.structureKey().serialized())).toList());
            byte[] expectedContent=NativeComboContent.encode(ordered,plan,1048576);
            var auth=authorization(c,plan,hash(expectedContent),new String((byte[])saved.get("resolved_context"),StandardCharsets.UTF_8));
            byte[] expectedEvidence=BatchCanonical.encode(Map.of("version","COMBO_DRAFT_EVIDENCE_V1","runId",c.run,
                    "miningReference",plan.context().publishedEvidenceRef(),"authorization",auth),1048576);
            if(!Arrays.equals(expectedContent,(byte[])saved.get("content_bytes"))||!Arrays.equals(store.plannedRepresentation(plan),(byte[])saved.get("expected_representation"))
                    ||!Arrays.equals(expectedEvidence,(byte[])saved.get("evidence_bytes"))
                    ||!Arrays.equals(store.reviewRepresentation(batch),(byte[])saved.get("expected_representation")))throw invalid("GENERATION_PAYLOAD_CHANGED");
            for(var pair:List.of(List.of("content_bytes","content_digest"),List.of("evidence_bytes","evidence_digest"),List.of("expected_representation","representation_digest")))
                if(!Arrays.equals(HexFormat.of().parseHex(hash((byte[])saved.get(pair.get(0)))),(byte[])saved.get(pair.get(1))))throw invalid("GENERATION_EVIDENCE_CORRUPT");
            if(!c.publication.equals(HexFormat.of().formatHex((byte[])saved.get("mining_receipt_digest"))))throw invalid("PUBLICATION_CHANGED");
            // Shared rules conservatively deny BOTH actions on changed commercial terms; no silent repricing.
            var used=new TreeSet<String>();current.items().forEach(i->used.add(i.productId()));
            var now=catalog(used,false);
            for(String id:used)if(!Objects.equals(c.catalog.get(id),now.get(id)))throw invalid("CATALOG_PRICE_OR_ELIGIBILITY_CHANGED");
            return new vn.edu.wspm.backend.services.combo.review.ComboReviewAuthority.Check(confirmed,
                    new vn.edu.wspm.backend.services.combo.review.ComboReview.Evidence(true,true),
                    hash(BatchCanonical.encode(List.of(c.version,c.publication,hash(expectedContent),now),2097152)));
        }catch(RuntimeException invalid){
            log.warn("Native Combo review verification rejected: batch={}, reason={}",current.row().batchId(),
                    invalid instanceof ApplicationException&&invalid.getMessage()!=null&&invalid.getMessage().matches("[A-Z_]{1,100}")?invalid.getMessage():invalid.getClass().getSimpleName());
            return new vn.edu.wspm.backend.services.combo.review.ComboReviewAuthority.Check(confirmed,
                vn.edu.wspm.backend.services.combo.review.ComboReview.Evidence.unverified(),
                invalid instanceof ApplicationException&&"CATALOG_PRICE_OR_ELIGIBILITY_CHANGED".equals(invalid.getMessage())
                    ?"CATALOG_PRICE_OR_ELIGIBILITY_CHANGED":"NATIVE_GENERATION_OR_CURRENT_CATALOG_INVALID");}
    }
    private String sign(Context c){byte[] bytes=json.writeValueAsBytes(c);if(bytes.length>262144)throw invalid("CONTEXT_LIMIT_EXCEEDED");
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac(bytes));}
    private byte[] mac(byte[] bytes){try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
        mac.update("TV2_NATIVE_COMBO_CONTEXT_V1\0".getBytes(StandardCharsets.UTF_8));return mac.doFinal(bytes);}catch(Exception e){throw new IllegalStateException("CONTEXT_SIGNING_UNAVAILABLE");}}
    private static void validate(String run,String anchor,Integer k,String identity){
        if(run==null||!run.matches("[0-9a-f-]{36}")||identity==null||!identity.matches("[a-zA-Z0-9_-]{1,128}")
                ||anchor!=null&&anchor.length()>40||k!=null&&(k<1||k>20))throw invalid("INVALID_GENERATION_COMMAND");
    }
    private static void outsideTransaction(){if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw invalid("OUTER_TRANSACTION_UNSUPPORTED");}
    private static String stable(String prefix,String value){return prefix+"-"+hash(value.getBytes(StandardCharsets.UTF_8)).substring(0,32);}
    private static String truncate(String s,int n){return s.codePointCount(0,s.length())>n?s.substring(0,s.offsetByCodePoints(0,n)):s;}
    public static String hash(byte[] bytes){return vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec.sha256(bytes);}
    private static ApplicationException invalid(String code){return new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE,code);}
}

