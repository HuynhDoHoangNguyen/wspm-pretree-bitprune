package vn.edu.wspm.backend.services.combo.generation;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import vn.edu.wspm.backend.dto.pattern.PatternDetailResponse;
import vn.edu.wspm.backend.services.combo.evaluation.CandidateEvaluation.Candidate;
import vn.edu.wspm.backend.services.combo.grouping.CandidateGrouping;

class DraftLimitTest {
    private CandidateGrouping.Group group(String id,String support,double weight,String canonical){
        var source=mock(PatternDetailResponse.class);
        when(source.supportAbs()).thenReturn(support);when(source.patternWeight()).thenReturn(weight);
        when(source.canonicalPattern()).thenReturn(canonical);
        var candidate=mock(Candidate.class);when(candidate.source()).thenReturn(source);when(candidate.patternId()).thenReturn(id);
        var group=mock(CandidateGrouping.Group.class);when(group.primary()).thenReturn(candidate);return group;
    }
    @Test void selectsGlobalSupportThenWeightWithStableTiesAndExactLargeSupport(){
        var a=group("1","9007199254740993",.1,"z");
        var b=group("2","9007199254740992",.9,"a");
        var c=group("3","8",.6,"z");var d=group("4","8",.5,"a");
        var e=group("10","7",.4,"same");var f=group("2","7",.4,"same");
        var input=new CandidateGrouping.Result(true,List.of(),List.of(e,d,b,f,c,a));
        assertEquals(List.of(a,b,c),NativeComboGeneration.selectDraftGroups(input,3).groups());
        assertEquals(List.of(a,b,c,d,f,e),NativeComboGeneration.selectDraftGroups(input,1000).groups());
        assertEquals(List.of(a),NativeComboGeneration.selectDraftGroups(input,1).groups());
        assertSame(input,NativeComboGeneration.selectDraftGroups(input,null));
        for(int invalid:new int[]{0,-1,1001})assertThrows(RuntimeException.class,()->NativeComboGeneration.selectDraftGroups(input,invalid));
        assertTrue(NativeComboGeneration.selectDraftGroups(new CandidateGrouping.Result(true,List.of(),List.of()),20).groups().isEmpty());
    }
}
