package com.dramastudio.service;

import com.dramastudio.engine.WorkflowExecutor;
import com.dramastudio.entity.*;
import com.dramastudio.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NodeGenerateService {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(NodeGenerateService.class);
 private final ProjectService projects;private final WorkflowRepository workflows;private final WorkflowService workflowService;
 private final WorkflowRunRepository runs;private final WorkflowExecutor executor;private final ObjectMapper json;
 /** 生成任务后台执行：视频动辄数十分钟，绝不能阻塞 HTTP 请求。 */
 private final java.util.concurrent.ExecutorService executorPool=
   java.util.concurrent.Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"node-generate");t.setDaemon(true);return t;});
 public NodeGenerateService(ProjectService p,WorkflowRepository w,WorkflowService ws,WorkflowRunRepository r,WorkflowExecutor e,ObjectMapper j){projects=p;workflows=w;workflowService=ws;runs=r;executor=e;json=j;}
 @jakarta.annotation.PreDestroy void shutdown(){executorPool.shutdown();}

 @SuppressWarnings("unchecked")
 public String generate(String projectId,String nodeId,String user,String prompt,String tool,Map<String,Object> params){
  projects.getOwned(projectId,user);
  if("上传".equals(tool))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"上传请用上传接口");
  Workflow workflow=null;Map<String,Object> snapshot=null;Map<String,Object> target=null;
  for(Workflow candidate:workflows.findByProjectIdAndDeletedAtIsNull(projectId)){
   Map<String,Object> canvas=parse(candidate.canvasJson);
   for(Map<String,Object> node:(List<Map<String,Object>>)canvas.getOrDefault("nodes",List.of()))if(nodeId.equals(Objects.toString(node.get("id")))){workflow=candidate;snapshot=canvas;target=node;break;}
   if(target!=null)break;
  }
  if(target==null||workflow==null||snapshot==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"节点不存在");
  Map<String,Object> data=(Map<String,Object>)target.computeIfAbsent("data",k->new LinkedHashMap<>());
  Map<String,Object> cfg=new LinkedHashMap<>((Map<String,Object>)data.getOrDefault("config",Map.of()));
  if(params!=null)cfg.putAll(params);if(tool!=null&&!tool.isBlank())cfg.put("tool",tool);if(prompt!=null)cfg.put("prompt",prompt);data.put("config",cfg);
  String type=Objects.toString(target.get("type"));String capability=WorkflowExecutor.effectiveCapability(type,cfg);
  if(Set.of("LLM","IMAGE","VIDEO").contains(capability)&&Objects.toString(cfg.get("prompt"),"").isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请填写提示词");
  WorkflowVersion version=workflowService.latestOrPublish(workflow.id,user);
  WorkflowRun run=new WorkflowRun();run.workflowId=workflow.id;run.workflowVersionId=version.id;run.projectId=projectId;run.targetNodeIdsJson=write(List.of(nodeId));run.status="QUEUED";run.inputSnapshotJson=write(Map.of("singleNode",true,"config",cfg));run.createdBy=user;
  runs.saveAndFlush(run);
  final String runId=run.id;final String targetNodeId=nodeId;final Map<String,Object> snap=snapshot;
  executorPool.submit(()->{try{executor.execute(runId,snap,List.of(targetNodeId));}
   catch(Exception e){log.warn("单节点生成失败 run={}: {}",runId,e.getMessage());}});
  return run.id;
 }
 private Map<String,Object> parse(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw new IllegalArgumentException(e);}}
 private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException(e);}}
}
