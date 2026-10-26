package com.dramastudio.template;
import com.fasterxml.jackson.databind.ObjectMapper; import java.util.*; import org.junit.jupiter.api.Test; import static org.assertj.core.api.Assertions.*;
class PromptTemplateEngineTest {
 @Test void resolvesUserThenUpstreamThenDefault(){var e=new PromptTemplateEngine(new ObjectMapper());var d=Map.<String,Object>of("sections",List.of(Map.of("key","a","label","A","defaultValue","default"),Map.of("key","b","label","B","defaultValue","default"),Map.of("key","c","label","C","defaultValue","default")));var r=e.resolve(d,Map.of("a","user"),Map.of("a","upstream","b","upstream"));assertThat(r.values()).containsEntry("a","user").containsEntry("b","upstream").containsEntry("c","default");assertThat(r.sources()).containsEntry("a","USER").containsEntry("b","UPSTREAM").containsEntry("c","DEFAULT");}
 @Test void blankOverrideFallsBack(){var e=new PromptTemplateEngine(new ObjectMapper());var d=Map.<String,Object>of("sections",List.of(Map.of("key","a","defaultValue","d")));assertThat(e.resolve(d,Map.of("a",""),Map.of("a","u")).values()).containsEntry("a","u");}
}
