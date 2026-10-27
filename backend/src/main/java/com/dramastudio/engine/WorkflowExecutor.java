package com.dramastudio.engine;

import com.dramastudio.entity.*;
import com.dramastudio.gateway.ProviderProtocol.*;
import com.dramastudio.repository.*;
import com.dramastudio.storage.OssStorageService;
import com.dramastudio.template.NodeCatalog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class WorkflowExecutor {
  private static final Logger log=LoggerFactory.getLogger(WorkflowExecutor.class);
  private final WorkflowRunRepository runs; private final NodeRunRepository nodeRuns;
  private final AssetRepository assets; private final GateService gates; private final RunEventHub events;
  private final ObjectMapper json; private final LlmProviderAdapter llm; private final ImageProviderAdapter image;
  private final VideoProviderAdapter video; private final PromptBuilder prompts; private final OssStorageService storage;

  public WorkflowExecutor(WorkflowRunRepository r,NodeRunRepository n,AssetRepository a,GateService g,
      RunEventHub e,ObjectMapper j,@Qualifier("mockLlm") LlmProviderAdapter mockL,
      @Qualifier("mockImage") ImageProviderAdapter mockI,@Qualifier("mockVideo") VideoProviderAdapter mockV,
      @Qualifier("aiConnectLlm") LlmProviderAdapter realL,@Qualifier("aiConnectImage") ImageProviderAdapter realI,
      @Qualifier("aiConnectVideo") VideoProviderAdapter realV,PromptBuilder pb,OssStorageService storage,Environment env){
    runs=r;nodeRuns=n;assets=a;gates=g;events=e;json=j;prompts=pb;this.storage=storage;
    String key=env.getProperty("app.aiconnect.api-key","");
    if(key!=null&&!key.isBlank()){llm=realL;image=realI;video=realV;log.info("Provider 模式: ai-connect");}
    else{llm=mockL;image=mockI;video=mockV;log.warn("Provider 模式: mock（未配置 AI_CONNECT_SK）");}
  }

  @Async @SuppressWarnings("unchecked")
  public void execute(String runId,Map<String,Object> snapshot,List<String> order){
    WorkflowRun run=runs.findById(runId).orElseThrow();
    run.status="RUNNING";run.startedAt=Instant.now().toString();runs.save(run);
    events.emit(runId,"RUN_STATUS_CHANGED",Map.of("status","RUNNING"));
    Map<String,Map<String,Object>> byId=new HashMap<>();
    for(var n:(List<Map<String,Object>>)snapshot.getOrDefault("nodes",List.of()))byId.put(Objects.toString(n.get("id")),n);
    markConnectedPorts(snapshot,byId);
    Set<String> branched=branchedDescendants(snapshot,byId);
    List<Integer> episodes=List.of(); int completed=0;
    ExecutorService pool=Executors.newFixedThreadPool(2);
    try{
      for(String id:order){
        run=runs.findById(runId).orElseThrow();if("CANCELED".equals(run.status))return;
        Map<String,Object> node=byId.get(id);String type=Objects.toString(node.get("type"));
        if("EPISODE_BRANCH".equals(type)){
          episodes=episodeNumbers(run.projectId);
          if(episodes.isEmpty())throw new ProviderException(ErrorCode.CONTENT_REJECTED,"分集扇出未找到有效 EPISODE_LIST");
          executeOne(run,node,0);completed++;events.emit(runId,"EPISODE_BRANCH_EXPANDED",Map.of("nodeId",id,"instanceCount",episodes.size()));
        }else if(branched.contains(id)&&!"EPISODE_OVERVIEW".equals(type)){
          List<Integer> selected=scopeEpisodes(type,config(node),episodes);
          List<Future<?>> jobs=new ArrayList<>();
          for(int episode:selected){WorkflowRun current=run;jobs.add(pool.submit(()->executeOne(current,node,episode)));}
          for(Future<?> job:jobs)job.get();
          completed++;
        }else{
          executeOne(run,node,0);completed++;
        }
        run=runs.findById(runId).orElseThrow();
        run.progress=BigDecimal.valueOf(completed*100.0/order.size());runs.save(run);
      }
      run=runs.findById(runId).orElseThrow();run.status="SUCCEEDED";run.progress=BigDecimal.valueOf(100);
      run.finishedAt=Instant.now().toString();runs.save(run);
      events.emit(runId,"RUN_STATUS_CHANGED",Map.of("status","SUCCEEDED","progress",100));
    }catch(Exception ex){
      Throwable cause=ex instanceof ExecutionException&&ex.getCause()!=null?ex.getCause():ex;
      run=runs.findById(runId).orElse(run);run.status=cause instanceof GateBlockedException?"WAITING_APPROVAL":"FAILED";
      run.finishedAt="FAILED".equals(run.status)?Instant.now().toString():null;runs.save(run);
      events.emit(runId,"RUN_STATUS_CHANGED",Map.of("status",run.status,"message",Objects.toString(cause.getMessage(),"")));
      log.warn("工作流 {} 执行中止: {}",runId,cause.getMessage());
    }finally{pool.shutdownNow();}
  }

  private void executeOne(WorkflowRun run,Map<String,Object> node,int episode){
    String id=Objects.toString(node.get("id")),type=Objects.toString(node.get("type"));
    Map<String,Object> cfg=new LinkedHashMap<>(config(node));if(episode>0)cfg.put("episode_number",episode);
    NodeRun nr=new NodeRun();nr.workflowRunId=run.id;nr.nodeId=id;nr.nodeType=type;nr.status="QUEUED";
    nr.idempotencyKey=run.id+":"+id+":"+episode;nr.configSnapshotJson=write(cfg);nodeRuns.save(nr);
    events.emit(run.id,"NODE_STATUS_CHANGED",Map.of("nodeId",id,"episodeNumber",episode,"status","QUEUED"));
    List<String> missing=gates.missing(run.projectId,type,cfg);
    if(!missing.isEmpty()){
      nr.status="BLOCKED";nr.errorCode="GATE_BLOCKED";nr.errorMessage=String.join("; ",missing);nodeRuns.save(nr);
      events.emit(run.id,"APPROVAL_REQUIRED",Map.of("nodeId",id,"episodeNumber",episode,"missingRequirements",missing));
      throw new GateBlockedException(nr.errorMessage);
    }
    try{
      nr.status="RUNNING";nr.startedAt=Instant.now().toString();nr.progress=BigDecimal.TEN;nodeRuns.save(nr);
      events.emit(run.id,"NODE_STATUS_CHANGED",Map.of("nodeId",id,"episodeNumber",episode,"status","RUNNING"));
      String capability=NodeCatalog.get(type).capability();
      if("NONE".equals(capability)){succeed(nr);return;}
      String prompt=prompts.build(type,cfg,run.projectId);nr.renderedPrompt=prompt;
      List<String> refs=prompts.resolveReferenceImages(run.projectId,type,cfg);
      nodeRuns.save(nr);
      Request request=new Request("ai-connect",prompt,Objects.toString(cfg.get("negative_prompt"),""),cfg,nr.idempotencyKey,refs);
      if("IMAGE_TO_VIDEO".equals(type)){
        if(refs.isEmpty())throw new ProviderException(ErrorCode.CONTENT_REJECTED,"同分集没有已选择且已落 OSS 的分镜图");
        int index=0;for(String ref:refs){Map<String,Object> shotCfg=new LinkedHashMap<>(cfg);shotCfg.put("shot_id",refs.size()==1?Objects.toString(cfg.get("shot_id"),"shot-1"):"shot-"+(++index));Request one=new Request(request.model(),request.prompt(),request.negativePrompt(),shotCfg,request.idempotencyKey()+":"+shotCfg.get("shot_id"),List.of(ref));Result videoResult=invoke("VIDEO",one,nr);if(!"SUCCEEDED".equalsIgnoreCase(videoResult.status())||videoResult.outputUrl()==null||videoResult.outputUrl().isBlank())throw new ProviderException(ErrorCode.PROVIDER_TIMEOUT,"视频通道当前不可用");for(Asset a:createAssets(run,nr,videoResult,shotCfg))events.emit(run.id,"ASSET_CREATED",Map.of("nodeId",id,"assetId",a.id,"episodeNumber",episode));}
        succeed(nr);return;
      }
      Result result="EPISODE_SPLITTER".equals(type)?splitEpisodes(request,cfg):invoke(capability,request,nr);
      if(!"SUCCEEDED".equalsIgnoreCase(result.status())||(("IMAGE".equals(capability)||"VIDEO".equals(capability))&&(result.outputUrl()==null||result.outputUrl().isBlank())))
        throw new ProviderException(ErrorCode.PROVIDER_TIMEOUT,"供应商未返回可用结果");
      succeed(nr);for(Asset a:createAssets(run,nr,result,cfg))events.emit(run.id,"ASSET_CREATED",Map.of("nodeId",id,"assetId",a.id,"episodeNumber",episode));
    }catch(Exception ex){
      nr.status="FAILED";nr.errorCode=ex instanceof ProviderException p?p.code().name():ErrorCode.PROVIDER_TIMEOUT.name();
      nr.errorMessage=Objects.toString(ex.getMessage(),ex.getClass().getSimpleName());nr.finishedAt=Instant.now().toString();nodeRuns.save(nr);
      events.emit(run.id,"NODE_STATUS_CHANGED",Map.of("nodeId",id,"episodeNumber",episode,"status","FAILED","errorCode",nr.errorCode));
      throw ex instanceof RuntimeException r?r:new RuntimeException(ex);
    }
  }

  private Result invoke(String capability,Request request,NodeRun nr){
    Adapter adapter="IMAGE".equals(capability)?image:"VIDEO".equals(capability)?video:llm;
    Submission sub=adapter.submit(request);nr.externalTaskId=sub.taskId();if(nr.workflowRunId!=null)nodeRuns.save(nr);
    if("SUCCEEDED".equalsIgnoreCase(sub.status()))return adapter.normalizeResult(sub.raw());
    Result result=adapter.query(sub.taskId());
    for(int i=0;i<60&&"RUNNING".equalsIgnoreCase(result.status());i++){
      try{Thread.sleep(5000);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new ProviderException(ErrorCode.PROVIDER_TIMEOUT,"供应商轮询被中断",e);}
      result=adapter.query(sub.taskId());
    }
    return result;
  }

  private Result splitEpisodes(Request base,Map<String,Object> cfg){
    int count=intValue(cfg.get("episode_count"),10);List<Map<String,Object>> all=new ArrayList<>();
    for(int from=1;from<=count;from+=5){
      int to=Math.min(count,from+4);Map<String,Object> batch=new LinkedHashMap<>(cfg);batch.put("batch_start",from);batch.put("batch_end",to);
      String prompt=prompts.build("EPISODE_SPLITTER",batch,latestRunProject(base.idempotencyKey()));
      Request req=new Request(base.model(),prompt,base.negativePrompt(),batch,base.idempotencyKey()+":batch"+from,List.of());
      Result result=invoke("LLM",req,newTransientRun());all.addAll(parseEpisodeArray(result.text(),from,to));
    }
    try{return new Result("SUCCEEDED",100,json.writeValueAsString(all),null,Map.of("episodeCount",all.size()));}
    catch(Exception e){throw new ProviderException(ErrorCode.CONTENT_REJECTED,"分集 JSON 合并失败",e);}
  }

  private String latestRunProject(String idempotencyKey){String runId=idempotencyKey.substring(0,idempotencyKey.indexOf(':'));return runs.findById(runId).orElseThrow().projectId;}
  private NodeRun newTransientRun(){NodeRun n=new NodeRun();n.id=UUID.randomUUID().toString();return n;}
  private List<Map<String,Object>> parseEpisodeArray(String text,int from,int to){
    try{
      String clean=text==null?"":text.trim().replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","");
      List<Map<String,Object>> list=json.readValue(clean,new TypeReference<>(){});
      if(list.size()!=to-from+1)throw new IllegalArgumentException("批次数量不符");
      for(int i=0;i<list.size();i++){Map<String,Object> x=list.get(i);x.put("episode_number",from+i);x.put("synopsis",limit(Objects.toString(x.get("synopsis"),""),40));x.put("hook",limit(Objects.toString(x.get("hook"),""),20));}
      return list;
    }catch(Exception e){throw new ProviderException(ErrorCode.CONTENT_REJECTED,"分集模型未返回合法 JSON: "+e.getMessage(),e);}
  }

  private List<Asset> createAssets(WorkflowRun run,NodeRun nr,Result result,Map<String,Object> cfg){
    if("SCENE_PROP_CARD".equals(nr.nodeType))return createScenePropAssets(run,nr,result,cfg);
    return List.of(createAsset(run,nr,result,cfg,outputType(nr.nodeType)));
  }
  @SuppressWarnings("unchecked") private List<Asset> createScenePropAssets(WorkflowRun run,NodeRun nr,Result result,Map<String,Object> cfg){
    try{Map<String,Object> parsed=json.readValue(result.text().replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$",""),new TypeReference<>(){});
      return List.of(createAsset(run,nr,new Result("SUCCEEDED",100,write(parsed.getOrDefault("scenes",List.of())),null,Map.of()),cfg,"SCENE_CARD_LIST"),createAsset(run,nr,new Result("SUCCEEDED",100,write(parsed.getOrDefault("props",List.of())),null,Map.of()),cfg,"PROP_CARD_LIST"));
    }catch(Exception e){throw new ProviderException(ErrorCode.CONTENT_REJECTED,"场景道具卡不是合法 JSON",e);}
  }
  private Asset createAsset(WorkflowRun run,NodeRun nr,Result result,Map<String,Object> cfg,String assetType){
    Asset a=new Asset();a.projectId=run.projectId;a.logicalKey=logicalKey(nr,cfg,assetType);
    a.versionNo=assets.findFirstByProjectIdAndLogicalKeyOrderByVersionNoDesc(a.projectId,a.logicalKey).map(x->x.versionNo+1).orElse(1);
    a.assetType=assetType;a.name=nr.nodeType+" 输出 v"+a.versionNo;a.contentStatus="draft";
    a.contentText=result.outputUrl()!=null?result.outputUrl():result.text();a.nodeRunId=nr.id;a.createdBy=run.createdBy;
    a.metadataJson=write(Map.of("episode_number",intValue(cfg.get("episode_number"),0),"shot_id",Objects.toString(cfg.get("shot_id"),"")));
    if(result.outputUrl()!=null&&!result.outputUrl().isBlank()){
      String ext=extension(result.outputUrl(),assetType.startsWith("SHOT_VIDEO")?"mp4":"png");
      a.mimeType=assetType.equals("SHOT_VIDEO")?"video/"+ext:"image/"+(ext.equals("jpg")?"jpeg":ext);
      a.storageKey=storage.objectKey(run.createdBy,run.projectId,assetType,a.logicalKey,a.versionNo,ext);
      long size=storage.copyFromUrl(result.outputUrl(),a.storageKey);if(size>=0)a.sizeBytes=size;
    }
    return assets.save(a);
  }
  private String logicalKey(NodeRun nr,Map<String,Object> cfg,String type){int ep=intValue(cfg.get("episode_number"),0);String shot=Objects.toString(cfg.get("shot_id"),nr.nodeId);
    if("SHOT_VIDEO".equals(type))return "shot_video:"+ep+":"+shot;
    if("STORYBOARD_IMAGE".equals(type))return "storyboard_image:"+ep+":"+shot;
    if("EPISODE_SCRIPT".equals(type))return "episode_script:"+ep;
    return nr.nodeType.toLowerCase(Locale.ROOT)+":"+(ep>0?ep+":"+nr.nodeId:nr.nodeId);
  }
  private String outputType(String t){return switch(t){case"PROJECT_SETUP"->"PROJECT_STATE";case"STORY_OUTLINE"->"OUTLINE";case"CHARACTER_CARD"->"CHARACTER_CARD_LIST";case"EPISODE_SPLITTER"->"EPISODE_LIST";case"EPISODE_SCRIPT"->"EPISODE_SCRIPT";case"STORYBOARD_SCRIPT"->"SHOT_CARD_LIST";case"CHARACTER_REFERENCE_IMAGE"->"CHARACTER_REFERENCE_IMAGE";case"SCENE_REFERENCE_IMAGE"->"SCENE_REFERENCE_IMAGE";case"PROP_REFERENCE_IMAGE"->"PROP_REFERENCE_IMAGE";case"STORYBOARD_IMAGE"->"STORYBOARD_IMAGE";case"IMAGE_TO_VIDEO"->"SHOT_VIDEO";default->"GENERIC_OUTPUT";};}

  private List<Integer> episodeNumbers(String projectId){
    String text=assets.findFirstByProjectIdAndAssetTypeOrderByVersionNoDesc(projectId,"EPISODE_LIST").map(a->a.contentText).orElse("");
    try{List<Map<String,Object>> list=json.readValue(text,new TypeReference<>(){});return list.stream().map(x->intValue(x.get("episode_number"),0)).filter(x->x>0).distinct().toList();}
    catch(Exception e){throw new ProviderException(ErrorCode.CONTENT_REJECTED,"EPISODE_LIST 不是合法 JSON",e);}
  }
  private List<Integer> scopeEpisodes(String type,Map<String,Object> cfg,List<Integer> all){
    if(!"EPISODE_SCRIPT".equals(type))return all;String scope=Objects.toString(cfg.getOrDefault("episode_scope","全部"));
    if("单集".equals(scope)){int n=intValue(cfg.get("episode_number"),1);return all.contains(n)?List.of(n):List.of();}
    if("多集".equals(scope)&&cfg.get("episode_list") instanceof Collection<?> c){Set<Integer> wanted=new HashSet<>();c.forEach(x->wanted.add(intValue(x,0)));return all.stream().filter(wanted::contains).toList();}
    return all;
  }
  @SuppressWarnings("unchecked") private Set<String> branchedDescendants(Map<String,Object> snapshot,Map<String,Map<String,Object>> byId){
    Map<String,List<String>> adj=new HashMap<>();byId.keySet().forEach(k->adj.put(k,new ArrayList<>()));
    for(Map<String,Object> e:(List<Map<String,Object>>)snapshot.getOrDefault("edges",List.of()))adj.getOrDefault(Objects.toString(e.get("source")),List.of()).add(Objects.toString(e.get("target")));
    Set<String> out=new HashSet<>();ArrayDeque<String> q=new ArrayDeque<>();byId.forEach((id,n)->{if("EPISODE_BRANCH".equals(n.get("type")))q.addAll(adj.get(id));});
    while(!q.isEmpty()){String id=q.remove();if(!out.add(id))continue;if(!"EPISODE_OVERVIEW".equals(Objects.toString(byId.get(id).get("type"))))q.addAll(adj.getOrDefault(id,List.of()));}
    return out;
  }
  @SuppressWarnings("unchecked") private void markConnectedPorts(Map<String,Object> snapshot,Map<String,Map<String,Object>> byId){
    for(Map<String,Object> e:(List<Map<String,Object>>)snapshot.getOrDefault("edges",List.of()))if("scene_assets".equals(Objects.toString(e.get("targetHandle")))){Map<String,Object> target=byId.get(Objects.toString(e.get("target")));if(target!=null)config(target).put("__scene_assets_connected",true);}
  }
  @SuppressWarnings("unchecked") private Map<String,Object> config(Map<String,Object> node){return (Map<String,Object>)((Map<String,Object>)node.getOrDefault("data",Map.of())).getOrDefault("config",Map.of());}
  private void succeed(NodeRun nr){nr.status="SUCCEEDED";nr.progress=BigDecimal.valueOf(100);nr.finishedAt=Instant.now().toString();nodeRuns.save(nr);events.emit(nr.workflowRunId,"NODE_STATUS_CHANGED",Map.of("nodeId",nr.nodeId,"status","SUCCEEDED"));}
  private String write(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalArgumentException(e);}}
  private static int intValue(Object x,int d){if(x instanceof Number n)return n.intValue();try{return x==null?d:Integer.parseInt(String.valueOf(x));}catch(Exception e){return d;}}
  private static String limit(String s,int n){return s.length()<=n?s:s.substring(0,n);}
  private static String extension(String url,String fallback){try{String p=URI.create(url).getPath();int dot=p.lastIndexOf('.');if(dot>=0){String x=p.substring(dot+1).toLowerCase(Locale.ROOT);if(x.matches("[a-z0-9]{2,5}"))return x;}}catch(Exception ignored){}return fallback;}
  private static final class GateBlockedException extends RuntimeException{GateBlockedException(String m){super(m);}}
}
