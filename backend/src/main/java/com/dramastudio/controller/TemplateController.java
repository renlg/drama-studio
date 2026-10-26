package com.dramastudio.controller;
import com.dramastudio.repository.*; import com.dramastudio.template.*; import com.fasterxml.jackson.databind.ObjectMapper; import java.util.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/templates") public class TemplateController {
 private final TemplateRepository templates;private final TemplateVersionRepository versions;private final ObjectMapper json;private final PromptTemplateEngine engine;public TemplateController(TemplateRepository t,TemplateVersionRepository v,ObjectMapper j,PromptTemplateEngine e){templates=t;versions=v;json=j;engine=e;}
 @GetMapping Object list() {return templates.findByDeletedAtIsNullOrderByNodeType().stream().map(t->Map.of("id",t.id,"name",t.name,"nodeType",t.nodeType,"scope",t.scope,"status",t.status,"definition",parse(versions.findFirstByTemplateIdOrderByCreatedAtDesc(t.id).orElseThrow().definitionJson))).toList();}
 @GetMapping("/node-catalog")Object catalog(){return NodeCatalog.all();}
 @PostMapping("/{id}/preview")Object preview(@PathVariable String id,@RequestBody Preview p){var v=versions.findFirstByTemplateIdOrderByCreatedAtDesc(id).orElseThrow();return engine.resolve(parse(v.definitionJson),p.overrides==null?Map.of():p.overrides,p.upstream==null?Map.of():p.upstream);}
 @SuppressWarnings("unchecked")private Map<String,Object> parse(String s){try{return json.readValue(s,Map.class);}catch(Exception e){throw new IllegalArgumentException(e);}}public record Preview(Map<String,Object> overrides,Map<String,Object> upstream){}
}
