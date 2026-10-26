package com.dramastudio.engine;
import java.util.*; import org.junit.jupiter.api.Test; import static org.assertj.core.api.Assertions.*;
class DagValidatorTest {
 private Map<String,Object> node(String id,String type){return Map.of("id",id,"type",type);}
 private Map<String,Object> edge(String s,String sh,String t,String th){return Map.of("source",s,"sourceHandle",sh,"target",t,"targetHandle",th);}
 @Test void sortsValidGraph(){var c=Map.<String,Object>of("nodes",List.of(node("a","PROJECT_SETUP"),node("b","STORY_OUTLINE")),"edges",List.of(edge("a","project_state","b","project_state")));var d=new DagValidator();assertThat(d.validate(c)).isEmpty();assertThat(d.topological(c)).containsExactly("a","b");}
 @Test void rejectsCycle(){var c=Map.<String,Object>of("nodes",List.of(node("a","GENERIC_LLM"),node("b","GENERIC_LLM")),"edges",List.of(edge("a","result","b","text"),edge("b","result","a","text")));assertThat(new DagValidator().validate(c)).extracting(DagValidator.Issue::code).contains("WF_CYCLE_DETECTED");}
}
