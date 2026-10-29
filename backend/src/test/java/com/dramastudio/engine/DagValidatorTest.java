package com.dramastudio.engine;
import java.util.*; import org.junit.jupiter.api.Test; import static org.assertj.core.api.Assertions.*;
class DagValidatorTest {
 private Map<String,Object> node(String id,String type){return Map.of("id",id,"type",type);}
 private Map<String,Object> edge(String s,String sh,String t,String th){return Map.of("source",s,"sourceHandle",sh,"target",t,"targetHandle",th);}
 @Test void sortsValidGraph(){var c=Map.<String,Object>of("nodes",List.of(node("a","TEXT"),node("b","TEXT")),"edges",List.of(edge("a","text","b","text")));var d=new DagValidator();assertThat(d.validate(c)).isEmpty();assertThat(d.topological(c)).containsExactly("a","b");}
 @Test void rejectsCycle(){var c=Map.<String,Object>of("nodes",List.of(node("a","TEXT"),node("b","TEXT")),"edges",List.of(edge("a","text","b","text"),edge("b","text","a","text")));assertThat(new DagValidator().validate(c)).extracting(DagValidator.Issue::code).contains("WF_CYCLE_DETECTED");}
}
