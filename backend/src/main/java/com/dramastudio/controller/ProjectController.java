package com.dramastudio.controller;
import com.dramastudio.entity.*; import com.dramastudio.repository.WorkflowRepository; import com.dramastudio.service.*; import jakarta.validation.Valid; import jakarta.validation.constraints.NotBlank; import java.util.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/projects") public class ProjectController {
 private final ProjectService service; private final CurrentUser current; private final WorkflowRepository workflows;
 public ProjectController(ProjectService s,CurrentUser c,WorkflowRepository w){service=s;current=c;workflows=w;}
 @GetMapping public List<Project> list(Authentication a){return service.list(current.id(a));}
 @PostMapping public Map<String,Object> create(@Valid @RequestBody Request r,Authentication a){Workflow w=service.create(new ProjectService.CreateProject(r.name,r.description,r.storyEnvironment,r.genres,r.dramaType,r.artStyle,r.episodeCount,r.episodeDurationSec),current.id(a));return Map.of("projectId",w.projectId,"workflowId",w.id);}
 @GetMapping("/{id}") public Project get(@PathVariable String id,Authentication a){return service.getOwned(id,current.id(a));}
 @GetMapping("/{id}/workflows") public List<Workflow> workflows(@PathVariable String id,Authentication a){service.getOwned(id,current.id(a));return workflows.findByProjectIdAndDeletedAtIsNull(id);}
 @PostMapping("/{id}/workflows/drama-template") public Map<String,Object> dramaTemplate(@PathVariable String id,Authentication a){Workflow w=service.createDramaTemplate(id,current.id(a));return Map.of("projectId",w.projectId,"workflowId",w.id);}
 public record Request(@NotBlank String name,String description,@NotBlank String storyEnvironment,List<String> genres,String dramaType,String artStyle,Integer episodeCount,Integer episodeDurationSec){}
}
