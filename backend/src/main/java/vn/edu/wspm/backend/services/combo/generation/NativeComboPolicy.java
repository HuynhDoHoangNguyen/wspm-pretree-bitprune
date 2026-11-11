package vn.edu.wspm.backend.services.combo.generation;

import java.math.BigInteger;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.wspm.backend.services.combo.evaluation.CandidateEvaluation;
import vn.edu.wspm.backend.services.combo.grouping.CandidateGrouping.WeightPolicyConfirmation;
import vn.edu.wspm.backend.services.combo.ranking.ComboPreview;
import vn.edu.wspm.backend.services.run.persistence.PatternWeightPolicy;

/** Owner decisions: attachment 2c84ad16, 2026-11-08. Business approval, not research acceptance. */
@Component
public final class NativeComboPolicy {
    public static final String VERSION="TV2_DEMO_CATALOG_V1";
    public static final String SOURCE="OWNER_20261108_2c84ad16";
    public final Set<String> laptops,accessories;
    public NativeComboPolicy(@Value("${tv2.combo.laptop-category-ids:}") String laptops,
            @Value("${tv2.combo.accessory-category-ids:}") String accessories) {
        this.laptops=ids(laptops);this.accessories=ids(accessories);
        if(!Collections.disjoint(this.laptops,this.accessories))throw new IllegalArgumentException("CATEGORY_POLICY_OVERLAP");
    }
    private static Set<String> ids(String text){
        if(text.isBlank())return Set.of();
        var result=new TreeSet<String>();for(String id:text.split(",",-1)){
            if(!id.equals(id.trim())||id.isBlank()||id.length()>40||!result.add(id))throw new IllegalArgumentException("INVALID_CATEGORY_CONFIG");}
        return Collections.unmodifiableSet(result);
    }
    public boolean configured(){return !laptops.isEmpty()&&!accessories.isEmpty();}
    public CandidateEvaluation.Policy evaluation(){return new CandidateEvaluation.Policy(VERSION,SOURCE,laptops,accessories,CandidateEvaluation.EligibilityMode.CATALOG_ONLY);}
    public ComboPreview.Configuration ranking(int k){
        if(k<1||k>20)throw new IllegalArgumentException("INVALID_K");
        return new ComboPreview.Configuration("SUPPORT_THEN_PATTERN_WEIGHT_V1",SOURCE,
                new WeightPolicyConfirmation(PatternWeightPolicy.VERSION,SOURCE),"FIRST_ITEMSET_V1",SOURCE,
                BigInteger.valueOf(k),BigInteger.valueOf(20),SOURCE);
    }
}
