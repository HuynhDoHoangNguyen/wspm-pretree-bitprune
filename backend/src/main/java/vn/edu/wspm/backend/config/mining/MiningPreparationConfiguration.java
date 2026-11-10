package vn.edu.wspm.backend.config.mining;

import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import vn.edu.wspm.backend.services.dataset.RawSnapshotStorage;
import vn.edu.wspm.backend.services.mining.provider.MiningPreparationService;

@Configuration(proxyBeanMethods=false)
public class MiningPreparationConfiguration {
    @Bean public MiningPreparationService miningPreparationService(Environment env,ObjectProvider<DataSource> ds,
                                                                  ObjectProvider<RawSnapshotStorage> storage) {
        String file=env.getProperty("tv2.mining-preparation.config-file");
        if(file==null || file.isBlank()) return MiningPreparationService.unavailable("PREPARATION_CONFIG_MISSING");
        return MiningPreparationService.fromFile(file,ds.getIfAvailable(),storage.getIfAvailable());
    }
}
