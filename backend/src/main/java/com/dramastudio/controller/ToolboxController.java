package com.dramastudio.controller;

import com.dramastudio.service.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/toolboxes") public class ToolboxController {
  private final ToolboxService service;private final CurrentUser current;
  public ToolboxController(ToolboxService service,CurrentUser current){this.service=service;this.current=current;}
  @GetMapping Object list(Authentication authentication){return service.list(current.id(authentication));}
  @PostMapping Object create(@RequestBody Request request,Authentication authentication){return service.create(current.id(authentication),request.name,request.nodes,request.edges);}
  @PutMapping("/{id}") Object update(@PathVariable String id,@RequestBody Request request,Authentication authentication){return service.update(current.id(authentication),id,request.name,request.nodes,request.edges);}
  @DeleteMapping("/{id}") void delete(@PathVariable String id,Authentication authentication){service.delete(current.id(authentication),id);}
  public record Request(String name,List<Map<String,Object>> nodes,List<Map<String,Object>> edges){}
}
