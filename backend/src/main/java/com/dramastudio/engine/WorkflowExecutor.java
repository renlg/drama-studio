package com.dramastudio.engine;

import com.dramastudio.entity.*;
import com.dramastudio.gateway.ProviderProtocol.*;
import com.dramastudio.repository.*;
import com.dramastudio.storage.OssStorageService;
import com.dramastudio.template.NodeCatalog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
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
  private final FfmpegService ffmpeg;

  public WorkflowExecutor(WorkflowRunRepository r,NodeRunRepository n,AssetRepository a,GateService g,
      RunEventHub e,ObjectMapper j,@Qualifier("mockLlm") LlmProviderAdapter mockL,
      @Qualifier("mockImage") ImageProviderAdapter mockI,@Qualifier("mockVideo") VideoProviderAdapter mockV,
      @Qualifier("aiConnectLlm") LlmProviderAdapter realL,@Qualifier("aiConnectImage") ImageProviderAdapter realI,
      @Qualifier("aiConnectVideo") VideoProviderAdapter realV,PromptBuilder pb,OssStorageService storage,FfmpegService ffmpeg,Environment env){
    runs=r;nodeRuns=n;assets=a;gates=g;events=e;json=j;prompts=pb;this.storage=storage;this.ffmpeg=ffmpeg;
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
      if("FFMPEG".equals(capability)){
        List<Asset> made=executeFfmpeg(run,nr,cfg);
        succeed(nr);for(Asset a:made)events.emit(run.id,"ASSET_CREATED",Map.of("nodeId",id,"assetId",a.id,"episodeNumber",episode));return;
      }
      String prompt=prompts.build(type,cfg,run.projectId);nr.renderedPrompt=prompt;
      List<String> refs=referenceUrls(run,type,cfg);
      nodeRuns.save(nr);
      Request request=new Request("ai-connect",prompt,Objects.toString(cfg.get("negative_prompt"),""),cfg,nr.idempotencyKey,refs);
      if("IMAGE_TO_VIDEO".equals(type)){
        if(refs.isEmpty())throw new ProviderException(ErrorCode.CONTENT_REJECTED,"同分集没有已选择且已落 OSS 的分镜图");
        int index=0;for(String ref:refs){Map<String,Object> shotCfg=new LinkedHashMap<>(cfg);shotCfg.put("shot_id",refs.size()==1?Objects.toString(cfg.get("shot_id"),"shot-1"):"shot-"+(++index));Request one=new Request(request.model(),request.prompt(),request.negativePrompt(),shotCfg,request.idempotencyKey()+":"+shotCfg.get("shot_id"),List.of(ref));Result videoResult=invoke("VIDEO",one,nr);if(!"SUCCEEDED".equalsIgnoreCase(videoResult.status())||videoResult.outputUrl()==null||videoResult.outputUrl().isBlank())throw new ProviderException(ErrorCode.PROVIDER_TIMEOUT,"视频通道当前不可用");for(Asset a:createAssets(run,nr,videoResult,shotCfg))events.emit(run.id,"ASSET_CREATED",Map.of("nodeId",id,"assetId",a.id,"episodeNumber",episode));}
        succeed(nr);return;
      }
      if("IMAGE_GEN".equals(type)){
        int count=Math.max(1,Math.min(20,intValue(cfg.get("output_count"),1)));Object baseSeed=cfg.get("seed");
        if(refs.size()>1)cfg.put("__warning","当前通道仅支持单参考图，已使用第 1 张");
        for(int i=0;i<count;i++){Map<String,Object> oneCfg=new LinkedHashMap<>(cfg);if(baseSeed instanceof Number n)oneCfg.put("seed",n.longValue()+i);oneCfg.put("output_index",i+1);
          Request one=new Request(request.model(),request.prompt(),request.negativePrompt(),oneCfg,request.idempotencyKey()+":"+(i+1),refs.isEmpty()?List.of():List.of(refs.get(0)));
          Result result=invoke("IMAGE",one,nr);validateMediaResult(result,"IMAGE");for(Asset a:createAssets(run,nr,result,oneCfg))events.emit(run.id,"ASSET_CREATED",Map.of("nodeId",id,"assetId",a.id,"episodeNumber",episode));}
        succeed(nr);return;
      }
      if("VIDEO_MULTI_KEYFRAME".equals(type)){
        if(refs.isEmpty())throw new ProviderException(ErrorCode.CONTENT_REJECTED,"智能多帧缺少输入图片");int count=Math.min(refs.size(),Math.max(1,intValue(cfg.get("max_shots"),5)));
        for(int i=0;i<count;i++){Map<String,Object> oneCfg=new LinkedHashMap<>(cfg);oneCfg.put("mode","keyframe");oneCfg.put("duration_sec",clampDuration(cfg.get("shot_duration_sec")));oneCfg.put("shot_id","multi-"+(i+1));
          Request one=new Request(request.model(),request.prompt(),request.negativePrompt(),oneCfg,request.idempotencyKey()+":"+(i+1),List.of(refs.get(i)));
          Result result=invoke("VIDEO",one,nr);validateMediaResult(result,"VIDEO");for(Asset a:createAssets(run,nr,result,oneCfg))events.emit(run.id,"ASSET_CREATED",Map.of("nodeId",id,"assetId",a.id,"episodeNumber",episode));}
        succeed(nr);return;
      }
      if("VIDEO_GEN".equals(type)){cfg.put("duration_sec",clampDuration(cfg.get("duration_sec")));String mode=Objects.toString(cfg.get("mode"),refs.isEmpty()?"text":"keyframe");if(refs.isEmpty()&&!"text".equals(mode))mode="text";cfg.put("mode",mode);request=new Request(request.model(),request.prompt(),request.negativePrompt(),cfg,request.idempotencyKey(),refs);}
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
    Map<String,Object> metadata=new LinkedHashMap<>();metadata.put("episode_number",intValue(cfg.get("episode_number"),0));metadata.put("shot_id",Objects.toString(cfg.get("shot_id"),""));
    if(cfg.get("__warning")!=null)metadata.put("warning",cfg.get("__warning"));a.metadataJson=write(metadata);
    if(result.outputUrl()!=null&&!result.outputUrl().isBlank()){
      String ext=extension(result.outputUrl(),assetType.startsWith("SHOT_VIDEO")?"mp4":"png");
      a.mimeType=assetType.equals("SHOT_VIDEO")?"video/"+ext:"image/"+(ext.equals("jpg")?"jpeg":ext);
      a.storageKey=storage.objectKey(run.createdBy,run.projectId,assetType,a.logicalKey,a.versionNo,ext);
      long size=storage.copyFromUrl(result.outputUrl(),a.storageKey);if(size>=0)a.sizeBytes=size;
    }
    return assets.save(a);
  }
  private String logicalKey(NodeRun nr,Map<String,Object> cfg,String type){int ep=intValue(cfg.get("episode_number"),0);String shot=Objects.toString(cfg.get("shot_id"),nr.nodeId);
    if(Set.of("TEXT_GEN","IMAGE_GEN","VIDEO_GEN","VIDEO_MULTI_KEYFRAME","VIDEO_CONCAT","MEDIA_TRIM","VIDEO_MUX_AUDIO","MEDIA_SCALE","MEDIA_EXTRACT_FRAME","IMAGE_CHROMA_KEY","VIDEO_INTERPOLATE").contains(nr.nodeType))return nr.nodeType.toLowerCase(Locale.ROOT)+":"+nr.nodeId+":"+nr.workflowRunId+":"+Objects.toString(cfg.getOrDefault("output_index",cfg.getOrDefault("shot_id","1")));
    if("SHOT_VIDEO".equals(type))return "shot_video:"+ep+":"+shot;
    if("STORYBOARD_IMAGE".equals(type))return "storyboard_image:"+ep+":"+shot;
    if("EPISODE_SCRIPT".equals(type))return "episode_script:"+ep;
    return nr.nodeType.toLowerCase(Locale.ROOT)+":"+(ep>0?ep+":"+nr.nodeId:nr.nodeId);
  }
  public static String outputType(String t){return switch(t){case"PROJECT_SETUP"->"PROJECT_STATE";case"STORY_OUTLINE"->"OUTLINE";case"CHARACTER_CARD"->"CHARACTER_CARD_LIST";case"EPISODE_SPLITTER"->"EPISODE_LIST";case"EPISODE_SCRIPT"->"EPISODE_SCRIPT";case"STORYBOARD_SCRIPT"->"SHOT_CARD_LIST";case"CHARACTER_REFERENCE_IMAGE"->"CHARACTER_REFERENCE_IMAGE";case"SCENE_REFERENCE_IMAGE"->"SCENE_REFERENCE_IMAGE";case"PROP_REFERENCE_IMAGE"->"PROP_REFERENCE_IMAGE";case"STORYBOARD_IMAGE","IMAGE_GEN","MEDIA_EXTRACT_FRAME","IMAGE_CHROMA_KEY"->"STORYBOARD_IMAGE";case"IMAGE_TO_VIDEO","VIDEO_GEN","VIDEO_MULTI_KEYFRAME","VIDEO_CONCAT","MEDIA_TRIM","VIDEO_MUX_AUDIO","MEDIA_SCALE","VIDEO_INTERPOLATE"->"SHOT_VIDEO";case"TEXT_GEN"->"TEXT";default->"GENERIC_OUTPUT";};}

  private void validateMediaResult(Result result,String capability){if(!"SUCCEEDED".equalsIgnoreCase(result.status())||result.outputUrl()==null||result.outputUrl().isBlank())throw new ProviderException(ErrorCode.PROVIDER_TIMEOUT,capability+" 供应商未返回可用结果");}
  private int clampDuration(Object value){return Math.max(4,Math.min(12,intValue(value,5)));}

  private List<String> referenceUrls(WorkflowRun run,String type,Map<String,Object> cfg){
    List<Asset> input=inputAssets(run.id,cfg);if(!input.isEmpty())return input.stream().filter(a->a.storageKey!=null&&!a.storageKey.isBlank()).map(a->storage.presignGet(a.storageKey,java.time.Duration.ofHours(24))).toList();
    return prompts.resolveReferenceImages(run.projectId,type,cfg);
  }
  @SuppressWarnings("unchecked") private List<Asset> inputAssets(String runId,Map<String,Object> cfg){
    Object raw=cfg.get("__input_node_ids");if(!(raw instanceof Map<?,?> ports))return List.of();Set<String> sourceIds=new LinkedHashSet<>();ports.values().forEach(v->{if(v instanceof Collection<?> c)c.forEach(x->sourceIds.add(String.valueOf(x)));else if(v!=null)sourceIds.add(String.valueOf(v));});
    List<Asset> out=new ArrayList<>();for(NodeRun source:nodeRuns.findByWorkflowRunIdOrderByCreatedAt(runId))if(sourceIds.contains(source.nodeId))out.addAll(assets.findByNodeRunIdOrderByCreatedAtDesc(source.id));Collections.reverse(out);return out;
  }

  private List<Asset> executeFfmpeg(WorkflowRun run,NodeRun nr,Map<String,Object> cfg){
    List<Asset> inputs=inputAssets(run.id,cfg);if(inputs.isEmpty())throw new ProviderException(ErrorCode.CONTENT_REJECTED,"剪辑节点缺少已落 OSS 的上游素材");Path dir=null;
    try{dir=Files.createTempDirectory("ds-ffmpeg-");List<Path> local=new ArrayList<>();int i=0;for(Asset a:inputs){if(a.storageKey==null||a.storageKey.isBlank())continue;String ext=a.mimeType!=null&&a.mimeType.contains("audio")?"m4a":a.mimeType!=null&&a.mimeType.contains("image")?"png":"mp4";Path p=dir.resolve("input-"+(++i)+"."+ext);storage.downloadTo(a.storageKey,p);local.add(p);}if(local.isEmpty())throw new ProviderException(ErrorCode.CONTENT_REJECTED,"剪辑节点输入素材没有 storage_key");
      List<Path> outputs=new ArrayList<>();String type=nr.nodeType;
      if("VIDEO_CONCAT".equals(type)){Path out=dir.resolve("merged.mp4");if("fade".equals(cfg.get("transition"))){List<Double> durations=local.stream().map(ffmpeg::duration).toList();ffmpeg.run(FfmpegService.fadeConcatCommand(local,durations,out,doubleValue(cfg.get("transition_duration"),.5)));}else{Path list=dir.resolve("list.txt");Files.writeString(list,local.stream().map(p->"file '"+p.toString().replace("'","'\\''")+"'").collect(java.util.stream.Collectors.joining("\n")),StandardCharsets.UTF_8);ffmpeg.run(FfmpegService.concatCommand(list,out,bool(cfg,"reencode",true)));}outputs.add(out);}
      else if("MEDIA_TRIM".equals(type)){Path out=dir.resolve("trim.mp4");ffmpeg.run(FfmpegService.trimCommand(local.get(0),out,doubleValue(cfg.get("start_sec"),0),nullableDouble(cfg.get("end_sec")),bool(cfg,"reencode",true)));outputs.add(out);}
      else if("MEDIA_SCALE".equals(type)){Path out=dir.resolve("scale.mp4");ffmpeg.run(FfmpegService.scaleCommand(local.get(0),out,intValue(cfg.get("width"),720),intValue(cfg.get("height"),1280),Objects.toString(cfg.get("fit"),"cover"),Objects.toString(cfg.get("pad_color"),"#000000")));outputs.add(out);}
      else if("VIDEO_MUX_AUDIO".equals(type)){if(local.size()<2)throw new ProviderException(ErrorCode.CONTENT_REJECTED,"视频配音需要视频和音频两个输入");Path out=dir.resolve("mux.mp4");ffmpeg.run(FfmpegService.muxCommand(local.get(0),local.get(1),out,doubleValue(cfg.get("audio_volume"),1),bool(cfg,"keep_original_audio",false),doubleValue(cfg.get("audio_offset_sec"),0),bool(cfg,"shortest",true)));outputs.add(out);}
      else if("MEDIA_EXTRACT_FRAME".equals(type)){Path pattern=dir.resolve("frame-%03d.png");ffmpeg.run(FfmpegService.extractCommand(local.get(0),pattern,Objects.toString(cfg.get("mode"),"single"),doubleValue(cfg.get("at_sec"),0),doubleValue(cfg.get("every_n_sec"),2),intValue(cfg.get("max_frames"),10)));try(var stream=Files.list(dir)){outputs.addAll(stream.filter(p->p.getFileName().toString().startsWith("frame-")).sorted().toList());}}
      else if("IMAGE_CHROMA_KEY".equals(type)){String ext="webp".equals(cfg.get("output_format"))?"webp":"png";int n=0;for(Path p:local){Path out=dir.resolve("chroma-"+(++n)+"."+ext);ffmpeg.run(FfmpegService.chromaCommand(p,out,Objects.toString(cfg.get("key_color"),"#FFFFFF"),doubleValue(cfg.get("similarity"),.12),doubleValue(cfg.get("blend"),.05)));outputs.add(out);}}
      else if("VIDEO_INTERPOLATE".equals(type)){Path out=dir.resolve("interpolated.mp4");ffmpeg.run(FfmpegService.interpolateCommand(local.get(0),out,intValue(cfg.get("fps"),30),Objects.toString(cfg.get("mode"),"mci")));outputs.add(out);}
      else throw new ProviderException(ErrorCode.CONTENT_REJECTED,"未知 FFmpeg 节点: "+type);
      List<Asset> made=new ArrayList<>();int index=0;for(Path output:outputs){Map<String,Object> one=new LinkedHashMap<>(cfg);one.put("output_index",++index);made.add(createLocalAsset(run,nr,one,output,outputType(type)));}return made;
    }catch(ProviderException e){throw e;}catch(Exception e){throw new ProviderException(ErrorCode.PROVIDER_TIMEOUT,"FFmpeg 节点失败: "+e.getMessage(),e);}finally{if(dir!=null)deleteTree(dir);}
  }
  private Asset createLocalAsset(WorkflowRun run,NodeRun nr,Map<String,Object> cfg,Path file,String assetType)throws IOException{Asset a=new Asset();a.projectId=run.projectId;a.logicalKey=logicalKey(nr,cfg,assetType);a.versionNo=assets.findFirstByProjectIdAndLogicalKeyOrderByVersionNoDesc(a.projectId,a.logicalKey).map(x->x.versionNo+1).orElse(1);a.assetType=assetType;a.name=nr.nodeType+" 输出 v"+a.versionNo;a.contentStatus="draft";a.nodeRunId=nr.id;a.createdBy=run.createdBy;String name=file.getFileName().toString();String ext=name.substring(name.lastIndexOf('.')+1);a.mimeType=assetType.equals("SHOT_VIDEO")?"video/"+ext:"image/"+ext;a.storageKey=storage.objectKey(run.createdBy,run.projectId,assetType,a.logicalKey,a.versionNo,ext);a.sizeBytes=storage.uploadFrom(file,a.storageKey);a.metadataJson=write(Map.of("output_index",cfg.getOrDefault("output_index",1)));return assets.save(a);}
  private void deleteTree(Path root){try(var walk=Files.walk(root)){walk.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}catch(IOException ignored){}}
  private static boolean bool(Map<String,Object> cfg,String key,boolean def){Object v=cfg.get(key);return v==null?def:v instanceof Boolean b?b:Boolean.parseBoolean(String.valueOf(v));}
  private static double doubleValue(Object v,double def){if(v instanceof Number n)return n.doubleValue();try{return v==null?def:Double.parseDouble(String.valueOf(v));}catch(Exception e){return def;}}
  private static Double nullableDouble(Object v){if(v==null||String.valueOf(v).isBlank())return null;return doubleValue(v,0);}

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
    for(Map<String,Object> e:(List<Map<String,Object>>)snapshot.getOrDefault("edges",List.of())){Map<String,Object> target=byId.get(Objects.toString(e.get("target")));if(target==null)continue;Map<String,Object> cfg=config(target);String port=Objects.toString(e.getOrDefault("targetHandle","input"));
      if("scene_assets".equals(port))cfg.put("__scene_assets_connected",true);
      Map<String,List<String>> input=(Map<String,List<String>>)cfg.computeIfAbsent("__input_node_ids",k->new LinkedHashMap<String,List<String>>());input.computeIfAbsent(port,k->new ArrayList<>()).add(Objects.toString(e.get("source")));
    }
  }
  @SuppressWarnings("unchecked") private Map<String,Object> config(Map<String,Object> node){return (Map<String,Object>)((Map<String,Object>)node.getOrDefault("data",Map.of())).getOrDefault("config",Map.of());}
  private void succeed(NodeRun nr){nr.status="SUCCEEDED";nr.progress=BigDecimal.valueOf(100);nr.finishedAt=Instant.now().toString();nodeRuns.save(nr);events.emit(nr.workflowRunId,"NODE_STATUS_CHANGED",Map.of("nodeId",nr.nodeId,"status","SUCCEEDED"));}
  private String write(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalArgumentException(e);}}
  private static int intValue(Object x,int d){if(x instanceof Number n)return n.intValue();try{return x==null?d:Integer.parseInt(String.valueOf(x));}catch(Exception e){return d;}}
  private static String limit(String s,int n){return s.length()<=n?s:s.substring(0,n);}
  private static String extension(String url,String fallback){try{String p=URI.create(url).getPath();int dot=p.lastIndexOf('.');if(dot>=0){String x=p.substring(dot+1).toLowerCase(Locale.ROOT);if(x.matches("[a-z0-9]{2,5}"))return x;}}catch(Exception ignored){}return fallback;}
  private static final class GateBlockedException extends RuntimeException{GateBlockedException(String m){super(m);}}
}
