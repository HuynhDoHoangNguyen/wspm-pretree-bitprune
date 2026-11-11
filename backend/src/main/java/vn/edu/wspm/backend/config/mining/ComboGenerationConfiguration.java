package vn.edu.wspm.backend.config.mining;

import java.time.Clock;
import java.util.ArrayList;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import vn.edu.wspm.backend.services.combo.generation.ComboGenerationService;
import vn.edu.wspm.backend.services.combo.persistence.JdbcDraftBatchStore;
import vn.edu.wspm.backend.services.mining.handoff.PersistedMiningResultBoundary;

/** No default approval or datasource access. Incomplete wiring leaves unrelated application startup intact. */
@Configuration(proxyBeanMethods=false)
public class ComboGenerationConfiguration {
    @Bean public ComboGenerationService comboGenerationService(ObjectProvider<PersistedMiningResultBoundary> sources,
            ObjectProvider<ComboGenerationService.BusinessAuthority> businesses,
            ObjectProvider<ComboGenerationService.WriterWitnessResolver> witnesses,ObjectProvider<JdbcDraftBatchStore> stores,
            ObjectProvider<vn.edu.wspm.backend.services.combo.generation.NativeComboGeneration> nativeGenerations) {
        var source=sources.getIfUnique();var business=businesses.getIfUnique();var witness=witnesses.getIfUnique();var store=stores.getIfUnique();
        var missing=new ArrayList<String>();
        if(source==null)missing.add("FULL_RESULT_BOUNDARY_UNAVAILABLE");
        if(business==null)missing.add("BUSINESS_AUTHORITY_UNAVAILABLE");
        if(witness==null)missing.add("WRITER_MINING_WITNESS_UNAVAILABLE");
        if(store==null)missing.add("DRAFT_STORE_UNAVAILABLE");
        var service=missing.isEmpty()?new ComboGenerationService(source,business,witness,store,Clock.systemUTC()):ComboGenerationService.unavailable(missing);
        var nativeGeneration=nativeGenerations.getIfUnique();return nativeGeneration==null?service:service.connectNative(nativeGeneration);
    }
}
