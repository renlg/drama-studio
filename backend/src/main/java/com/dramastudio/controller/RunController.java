package com.dramastudio.controller;
import com.dramastudio.engine.RunEventHub; import com.dramastudio.entity.*; import com.dramastudio.service.*; import java.util.*; import org.springframework.http.MediaType; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
@RestController @RequestMapping("/api/v1") public class RunController {
 private final RunService runs;private final CurrentUser current;private final RunEventHub events;public RunController(RunService r,CurrentUser c,RunEventHub e){runs=r;current=c;events=e;}
 @PostMapping("/workflows/{id}/runs")WorkflowRun start(@PathVariable String id,@RequestBody(required=false)RunRequest r,Authentication a){return runs.start(id,r==null?List.of():r.targetNodeIds,current.id(a));}
 @GetMapping("/runs")List<WorkflowRun> list(Authentication a){return runs.list(current.id(a));}
 @GetMapping("/runs/{id}")WorkflowRun get(@PathVariable String id,Authentication a){return runs.get(id,current.id(a));}
 @GetMapping("/runs/{id}/node-runs")List<NodeRun> nodes(@PathVariable String id,Authentication a){return runs.nodeRuns(id,current.id(a));}
 @PostMapping("/runs/{id}/cancel")void cancel(@PathVariable String id,Authentication a){runs.cancel(id,current.id(a));}
 @GetMapping(value="/runs/{id}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)SseEmitter stream(@PathVariable String id,@RequestHeader(value="Last-Event-ID",defaultValue="0")long last,Authentication a){runs.get(id,current.id(a));return events.subscribe(id,last);}
 public record RunRequest(List<String> targetNodeIds){}
}
