package vn.edu.wspm.backend.services.recommendation;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.backend.repositories.recommendation.ComboReadRepository.*;

class HistoryPrefixMatcherTest {
    final HistoryPrefixMatcher matcher = new HistoryPrefixMatcher();
    ItemRow item(int step, int pos, String product) {
        return new ItemRow("c",step,pos,1,step==1,true,null,product,product,null,null,null,null);
    }
    List<HistoryRow> history(String... orders) {
        var rows = new ArrayList<HistoryRow>();
        for (int i=0;i<orders.length;i++) for (String p:orders[i].split(""))
            rows.add(new HistoryRow("o"+i, Instant.EPOCH.plusSeconds(i),p));
        return rows;
    }
    @Test void mouseThenSpeakerUsesOnlyNextUnboughtStep() {
        var items=List.of(item(1,1,"a"),item(2,1,"b"),item(3,1,"c"));
        var result=matcher.match(items,history("a","x")).orElseThrow();
        assertEquals(1,result.matchedItemsets()); assertEquals(2,result.nextItemsetOrder());
        assertEquals(List.of("b"),result.suggestedProductIds());
        assertTrue(matcher.match(items,history("b","a")).isEmpty()); // next step already bought
        assertEquals(List.of("c"),matcher.match(items,history("a","b")).orElseThrow().suggestedProductIds());
        assertTrue(matcher.match(items,history("a","b","c")).isEmpty());
    }
    @Test void mixedPrefixRequiresWholeItemsetInOneOrderAndDistinctTemporalOrders() {
        var mixed=List.of(item(1,1,"a"),item(1,2,"b"),item(2,1,"c"));
        assertTrue(matcher.match(mixed,history("a","b")).isEmpty());
        assertEquals(List.of("c"),matcher.match(mixed,history("ab")).orElseThrow().suggestedProductIds());
        assertTrue(matcher.match(mixed,history("abc")).isEmpty()); // next product bought, not a future purchase
        assertTrue(matcher.match(List.of(item(1,1,"a"),item(1,2,"b")),history("a")).isEmpty());
    }
    @Test void equalTimeOrdersRemainDistinctAndEarliestMatchDoesNotLoseLaterPrefix() {
        var h=history("a","a","b");
        h=h.stream().map(r->new HistoryRow(r.orderId(),Instant.EPOCH,r.productId())).toList();
        assertEquals(2,matcher.match(List.of(item(1,1,"a"),item(2,1,"b"),item(3,1,"c")),h)
                .orElseThrow().matchedItemsets());
    }
    @Test void greedyPrefixMatchesExhaustiveEmbeddingOnTinyHistories() {
        var random=new Random(20261110);
        for(int trial=0;trial<500;trial++) {
            var steps=new ArrayList<Set<String>>(); var items=new ArrayList<ItemRow>();
            for(int s=0;s<3;s++) {
                var set=new TreeSet<String>();set.add(String.valueOf((char)('a'+s)));
                if(random.nextBoolean()) set.add("x");steps.add(set);
                int pos=0;for(String p:set)items.add(item(s+1,++pos,p));
            }
            var orders=new ArrayList<Set<String>>();var h=new ArrayList<HistoryRow>();
            for(int o=0;o<5;o++) {
                var set=new TreeSet<String>();for(String p:List.of("a","b","c","x"))if(random.nextBoolean())set.add(p);
                orders.add(set);for(String p:set)h.add(new HistoryRow("o"+o,Instant.EPOCH,p));
            }
            int longest=0;for(int n=1;n<=3;n++)if(embeds(steps,orders,0,0,n))longest=n;
            var bought=new HashSet<String>();orders.forEach(bought::addAll);
            var expected=longest>0&&longest<3?new TreeSet<>(steps.get(longest)):new TreeSet<String>();expected.removeAll(bought);
            var result=matcher.match(items,h);
            assertEquals(!expected.isEmpty(),result.isPresent(),"trial="+trial);
            if(result.isPresent()) {assertEquals(longest,result.get().matchedItemsets());assertEquals(expected,new TreeSet<>(result.get().suggestedProductIds()));}
        }
    }
    boolean embeds(List<Set<String>> steps,List<Set<String>> orders,int s,int o,int length) {
        if(s==length)return true;
        for(int i=o;i<orders.size();i++)if(orders.get(i).containsAll(steps.get(s))&&embeds(steps,orders,s+1,i+1,length))return true;
        return false;
    }
}
