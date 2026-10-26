package com.dramastudio.controller;
import com.dramastudio.engine.DagValidator; import com.dramastudio.entity.*; import com.dramastudio.service.*; import java.util.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/workflows") public class WorkflowController {
 private final WorkflowService service;private final CurrentUser current;public WorkflowController(WorkflowService s,CurrentUser c){service=s;current=c;}
 @GetMapping("/{id}")Object get(@PathVariable String id,Authentication a){return service.detail(id,current.id(a));}
 @PutMapping("/{id}")Object save(@PathVariable String id,@RequestBody SaveRequest r,Authentication a){Workflow w=service.save(id,r.revision,r.name,r.canvas,current.id(a));return Map.of("id",w.id,"revision",w.revision);}
 @PostMapping("/{id}/validate")Object validate(@PathVariable String id,Authentication a){List<DagValidator.Issue> i=service.validate(id,current.id(a));return Map.of("valid",i.isEmpty(),"errors",i);}
 @PostMapping("/{id}/publish")WorkflowVersion publish(@PathVariable String id,Authentication a){return service.publish(id,current.id(a));}
 public record SaveRequest(long revision,String name,Map<String,Object> canvas){}
}
