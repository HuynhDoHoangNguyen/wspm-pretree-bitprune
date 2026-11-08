package vn.edu.wspm.backend.config.mining;
import org.springframework.context.annotation.*;
import vn.edu.wspm.backend.services.combo.generation.NativeComboGeneration;
import vn.edu.wspm.backend.services.combo.review.ComboReviewAuthority;
@Configuration(proxyBeanMethods=false)
public class NativeComboConfiguration {
    @Bean ComboReviewAuthority nativeComboReviewAuthority(NativeComboGeneration nativeGeneration){return nativeGeneration::review;}
}
