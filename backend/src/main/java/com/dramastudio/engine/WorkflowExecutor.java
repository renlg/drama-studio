package com.dramastudio.engine;

import com.dramastudio.entity.*;
import com.dramastudio.gateway.ProviderProtocol.*;
import com.dramastudio.repository.*;
import com.dramastudio.storage.OssStorageService;
import com.dramastudio.template.NodeCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
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
    int completed=0;
    try{
      for(String id:order){
        run=runs.findById(runId).orElseThrow();if("CANCELED".equals(run.status))return;
        executeOne(run,byId.get(id),0);completed++;
        run=runs.findById(runId).orElseThrow();
        run.progress=BigDecimal.valueOf(completed*100.0/order.size());runs.save(run);
      }
      run=runs.findById(runId).orElseThrow();run.status="SUCCEEDED";run.progress=BigDecimal.valueOf(100);
      run.finishedAt=Instant.now().toString();runs.save(run);
      events.emit(runId,"RUN_STATUS_CHANGED",Map.of("status","SUCCEEDED","progress",100));
    }catch(Exception ex){
      Throwable cause=ex;
      run=runs.findById(runId).orElse(run);run.status=cause instanceof GateBlockedException?"WAITING_APPROVAL":"FAILED";
      run.finishedAt="FAILED".equals(run.status)?Instant.now().toString():null;runs.save(run);
      events.emit(runId,"RUN_STATUS_CHANGED",Map.of("status",run.status,"message",Objects.toString(cause.getMessage(),"")));
      log.warn("工作流 {} 执行中止: {}",runId,cause.getMessage());
    }
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
      String tool=Objects.toString(cfg.get("tool"),"");
      String capability=effectiveCapability(type,cfg);
      if("NONE".equals(capability)){
        succeed(nr);return;
      }
      if("FFMPEG".equals(capability)){
        List<Asset> made=executeFfmpeg(run,nr,cfg,type,tool);
        succeed(nr);for(Asset a:made)events.emit(run.id,"ASSET_CREATED",Map.of("nodeId",id,"assetId",a.id,"episodeNumber",episode));return;
      }
      String prompt=prompts.build(type,cfg,run.projectId);nr.renderedPrompt=prompt;
      List<String> refs=referenceUrls(run,type,cfg);
      nodeRuns.save(nr);
      Request request=new Request("ai-connect",prompt,Objects.toString(cfg.get("negative_prompt"),""),cfg,nr.idempotencyKey,refs);
      if("VIDEO".equals(type)&&"图生视频".equals(tool)){
        if(refs.isEmpty())throw new ProviderException(ErrorCode.CONTENT_REJECTED,"同分集没有已选择且已落 OSS 的分镜图");
        int index=0;for(String ref:refs){Map<String,Object> shotCfg=new LinkedHashMap<>(cfg);shotCfg.put("shot_id",refs.size()==1?Objects.toString(cfg.get("shot_id"),"shot-1"):"shot-"+(++index));Request one=new Request(request.model(),request.prompt(),request.negativePrompt(),shotCfg,request.idempotencyKey()+":"+shotCfg.get("shot_id"),List.of(ref));Result videoResult=invoke("VIDEO",one,nr);if(!"SUCCEEDED".equalsIgnoreCase(videoResult.status())||videoResult.outputUrl()==null||videoResult.outputUrl().isBlank())throw new ProviderException(ErrorCode.PROVIDER_TIMEOUT,"视频通道当前不可用");for(Asset a:createAssets(run,nr,videoResult,shotCfg))events.emit(run.id,"ASSET_CREATED",Map.of("nodeId",id,"assetId",a.id,"episodeNumber",episode));}
        succeed(nr);return;
      }
      if("IMAGE".equals(capability)){
        int count="宫格生成".equals(tool)?gridCount(cfg):Math.max(1,Math.min(20,intValue(cfg.get("output_count"),1)));Object baseSeed=cfg.get("seed");
        if(refs.size()>1)cfg.put("__warning","当前通道仅支持单参考图，已使用第 1 张");
        for(int i=0;i<count;i++){Map<String,Object> oneCfg=new LinkedHashMap<>(cfg);if(baseSeed instanceof Number n)oneCfg.put("seed",n.longValue()+i);oneCfg.put("output_index",i+1);
          Request one=new Request(request.model(),request.prompt(),request.negativePrompt(),oneCfg,request.idempotencyKey()+":"+(i+1),refs.isEmpty()?List.of():List.of(refs.get(0)));
          Result result=invoke("IMAGE",one,nr);validateMediaResult(result,"IMAGE");for(Asset a:createAssets(run,nr,result,oneCfg))events.emit(run.id,"ASSET_CREATED",Map.of("nodeId",id,"assetId",a.id,"episodeNumber",episode));}
        succeed(nr);return;
      }
      if("VIDEO".equals(type)&&"智能多帧".equals(tool)){
        if(refs.isEmpty())throw new ProviderException(ErrorCode.CONTENT_REJECTED,"智能多帧缺少输入图片");int count=Math.min(refs.size(),Math.max(1,intValue(cfg.get("max_shots"),5)));
        for(int i=0;i<count;i++){Map<String,Object> oneCfg=new LinkedHashMap<>(cfg);oneCfg.put("mode","keyframe");oneCfg.put("duration_sec",clampDuration(cfg.get("shot_duration_sec")));oneCfg.put("shot_id","multi-"+(i+1));
          Request one=new Request(request.model(),request.prompt(),request.negativePrompt(),oneCfg,request.idempotencyKey()+":"+(i+1),List.of(refs.get(i)));
          Result result=invoke("VIDEO",one,nr);validateMediaResult(result,"VIDEO");for(Asset a:createAssets(run,nr,result,oneCfg))events.emit(run.id,"ASSET_CREATED",Map.of("nodeId",id,"assetId",a.id,"episodeNumber",episode));}
        succeed(nr);return;
      }
      if("VIDEO".equals(type)&&"文生视频".equals(tool)){cfg.put("duration_sec",clampDuration(cfg.get("duration_sec")));cfg.put("mode","text");request=new Request(request.model(),request.prompt(),request.negativePrompt(),cfg,request.idempotencyKey(),List.of());}
      Result result=invoke(capability,request,nr);
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

  private List<Asset> createAssets(WorkflowRun run,NodeRun nr,Result result,Map<String,Object> cfg){
    return List.of(createAsset(run,nr,result,cfg,outputType(nr.nodeType,Objects.toString(cfg.get("tool"),""))));
  }
  private Asset createAsset(WorkflowRun run,NodeRun nr,Result result,Map<String,Object> cfg,String assetType){
    Asset a=new Asset();a.projectId=run.projectId;a.logicalKey=logicalKey(nr,cfg,assetType);
    a.versionNo=assets.findFirstByProjectIdAndLogicalKeyOrderByVersionNoDesc(a.projectId,a.logicalKey).map(x->x.versionNo+1).orElse(1);
    a.assetType=assetType;a.name=nr.nodeType+" 输出 v"+a.versionNo;a.contentStatus="draft";
    a.contentText=result.outputUrl()!=null?result.outputUrl():result.text();a.nodeRunId=nr.id;a.createdBy=run.createdBy;
    Map<String,Object> metadata=new LinkedHashMap<>();metadata.put("episode_number",intValue(cfg.get("episode_number"),0));metadata.put("shot_id",Objects.toString(cfg.get("shot_id"),""));
    if(cfg.get("__warning")!=null)metadata.put("warning",cfg.get("__warning"));a.metadataJson=write(metadata);
    if(result.outputUrl()!=null&&!result.outputUrl().isBlank()){
      String ext=extension(result.outputUrl(),"VIDEO".equals(assetType)?"mp4":"AUDIO".equals(assetType)?"m4a":"png");
      a.mimeType="VIDEO".equals(assetType)?"video/"+ext:"AUDIO".equals(assetType)?"audio/"+ext:"image/"+(ext.equals("jpg")?"jpeg":ext);
      a.storageKey=storage.objectKey(run.createdBy,run.projectId,assetType,a.logicalKey,a.versionNo,ext);
      long size=storage.copyFromUrl(result.outputUrl(),a.storageKey);if(size>=0)a.sizeBytes=size;
    }
    return assets.save(a);
  }
  private String logicalKey(NodeRun nr,Map<String,Object> cfg,String type){int ep=intValue(cfg.get("episode_number"),0);String shot=Objects.toString(cfg.get("shot_id"),nr.nodeId);
    return nr.nodeType.toLowerCase(Locale.ROOT)+":"+nr.nodeId+":"+nr.workflowRunId+":"+Objects.toString(cfg.getOrDefault("output_index",cfg.getOrDefault("shot_id","1")));
  }
  public static String outputType(String type,String tool){return switch(type){
    case "TEXT"->"TEXT";case "IMAGE","CHARACTER","SCENE"->"IMAGE";
    case "VIDEO"->"抽帧".equals(tool)?"IMAGE":"音频分离".equals(tool)?"AUDIO":"VIDEO";
    case "AUDIO"->"AUDIO";default->"GENERIC_OUTPUT";};}

  static String effectiveCapability(String type,Map<String,Object> cfg){
    if("TEXT".equals(type))return "LLM";String tool=Objects.toString(cfg.get("tool"),"");
    if(Set.of("IMAGE","CHARACTER","SCENE").contains(type)){
      if(Set.of("抠图","裁剪","旋转","宫格切分").contains(tool))return "FFMPEG";
      if("上传".equals(tool))return "NONE";return "IMAGE";
    }
    if("VIDEO".equals(type))return Set.of("文生视频","图生视频","智能多帧").contains(tool)?"VIDEO":"FFMPEG";
    if("AUDIO".equals(type))return "上传".equals(tool)?"NONE":"FFMPEG";
    return "NONE";
  }

  private static int gridCount(Map<String,Object> cfg){String kind=Objects.toString(cfg.get("grid_type"),"");if("三视图".equals(kind))return 3;if("表情九宫格".equals(kind)||"九宫格".equals(kind))return 9;if("四宫格".equals(kind))return 4;return Math.max(1,Math.min(9,intValue(cfg.get("rows"),1)*intValue(cfg.get("cols"),1)));}

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

  private List<Asset> executeFfmpeg(WorkflowRun run,NodeRun nr,Map<String,Object> cfg,String type,String tool){
    List<Asset> inputs=inputAssets(run.id,cfg);if(inputs.isEmpty())throw new ProviderException(ErrorCode.CONTENT_REJECTED,"剪辑节点缺少已落 OSS 的上游素材");Path dir=null;
    try{dir=Files.createTempDirectory("ds-ffmpeg-");List<Path> local=new ArrayList<>();int i=0;for(Asset a:inputs){if(a.storageKey==null||a.storageKey.isBlank())continue;String ext=a.mimeType!=null&&a.mimeType.contains("audio")?"m4a":a.mimeType!=null&&a.mimeType.contains("image")?"png":"mp4";Path p=dir.resolve("input-"+(++i)+"."+ext);storage.downloadTo(a.storageKey,p);local.add(p);}if(local.isEmpty())throw new ProviderException(ErrorCode.CONTENT_REJECTED,"剪辑节点输入素材没有 storage_key");
      List<Path> outputs=new ArrayList<>();
      if("VIDEO".equals(type)&&"拼接".equals(tool)){Path out=dir.resolve("merged.mp4");if("fade".equals(cfg.get("transition"))){List<Double> durations=local.stream().map(ffmpeg::duration).toList();ffmpeg.run(FfmpegService.fadeConcatCommand(local,durations,out,doubleValue(cfg.get("transition_duration"),.5)));}else{Path list=dir.resolve("list.txt");Files.writeString(list,local.stream().map(p->"file '"+p.toString().replace("'","'\\''")+"'").collect(java.util.stream.Collectors.joining("\n")),StandardCharsets.UTF_8);ffmpeg.run(FfmpegService.concatCommand(list,out,bool(cfg,"reencode",true)));}outputs.add(out);}
      else if("VIDEO".equals(type)&&"截取".equals(tool)){Path out=dir.resolve("trim.mp4");ffmpeg.run(FfmpegService.trimCommand(local.get(0),out,doubleValue(cfg.get("start_sec"),0),nullableDouble(cfg.get("end_sec")),bool(cfg,"reencode",true)));outputs.add(out);}
      else if("VIDEO".equals(type)&&"分辨率调整".equals(tool)){Path out=dir.resolve("scale.mp4");ffmpeg.run(FfmpegService.scaleCommand(local.get(0),out,intValue(cfg.get("width"),720),intValue(cfg.get("height"),1280),Objects.toString(cfg.get("fit"),"cover"),Objects.toString(cfg.get("pad_color"),"#000000")));outputs.add(out);}
      else if("VIDEO".equals(type)&&"配音配乐".equals(tool)){if(local.size()<2)throw new ProviderException(ErrorCode.CONTENT_REJECTED,"视频配音需要视频和音频两个输入");Path out=dir.resolve("mux.mp4");ffmpeg.run(FfmpegService.muxCommand(local.get(0),local.get(1),out,doubleValue(cfg.get("audio_volume"),1),bool(cfg,"keep_original_audio",false),doubleValue(cfg.get("audio_offset_sec"),0),bool(cfg,"shortest",true)));outputs.add(out);}
      else if("VIDEO".equals(type)&&"抽帧".equals(tool)){Path pattern=dir.resolve("frame-%03d.png");ffmpeg.run(FfmpegService.extractCommand(local.get(0),pattern,Objects.toString(cfg.get("extract_mode"),"single"),doubleValue(cfg.get("at_sec"),0),doubleValue(cfg.get("every_n_sec"),2),intValue(cfg.get("max_frames"),10)));try(var stream=Files.list(dir)){outputs.addAll(stream.filter(p->p.getFileName().toString().startsWith("frame-")).sorted().toList());}}
      else if("IMAGE".equals(type)&&"抠图".equals(tool)){String ext="webp".equals(cfg.get("output_format"))?"webp":"png";int n=0;for(Path p:local){Path out=dir.resolve("chroma-"+(++n)+"."+ext);ffmpeg.run(FfmpegService.chromaCommand(p,out,Objects.toString(cfg.get("key_color"),"#FFFFFF"),doubleValue(cfg.get("similarity"),.12),doubleValue(cfg.get("blend"),.05)));outputs.add(out);}}
      else if("VIDEO".equals(type)&&"补帧".equals(tool)){Path out=dir.resolve("interpolated.mp4");ffmpeg.run(FfmpegService.interpolateCommand(local.get(0),out,intValue(cfg.get("fps"),30),Objects.toString(cfg.get("interpolation_mode"),"mci")));outputs.add(out);}
      else if("IMAGE".equals(type)&&"裁剪".equals(tool)){int n=0;for(Path p:local){Path out=dir.resolve("crop-"+(++n)+".png");ffmpeg.run(FfmpegService.imageCropCommand(p,out,intValue(cfg.get("x"),0),intValue(cfg.get("y"),0),intValue(cfg.get("width"),512),intValue(cfg.get("height"),512)));outputs.add(out);}}
      else if("IMAGE".equals(type)&&"旋转".equals(tool)){int n=0;for(Path p:local){Path out=dir.resolve("rotate-"+(++n)+".png");ffmpeg.run(FfmpegService.rotateCommand(p,out,intValue(cfg.get("angle"),90),true));outputs.add(out);}}
      else if("IMAGE".equals(type)&&"宫格切分".equals(tool)){int rows=Math.max(1,Math.min(9,intValue(cfg.get("rows"),3))),cols=Math.max(1,Math.min(9,intValue(cfg.get("cols"),3)));for(int r=0;r<rows;r++)for(int c=0;c<cols;c++){Path out=dir.resolve("cell-"+r+"-"+c+".png");ffmpeg.run(FfmpegService.gridCellCommand(local.get(0),out,rows,cols,r,c));outputs.add(out);}}
      else if("VIDEO".equals(type)&&"旋转".equals(tool)){Path out=dir.resolve("rotate.mp4");ffmpeg.run(FfmpegService.rotateCommand(local.get(0),out,intValue(cfg.get("angle"),90),false));outputs.add(out);}
      else if("VIDEO".equals(type)&&"倒放".equals(tool)){Path out=dir.resolve("reverse.mp4");ffmpeg.run(FfmpegService.reverseCommand(local.get(0),out,bool(cfg,"reverse_audio",true)));outputs.add(out);}
      else if("VIDEO".equals(type)&&"倍速".equals(tool)){Path out=dir.resolve("speed.mp4");ffmpeg.run(FfmpegService.speedCommand(local.get(0),out,doubleValue(cfg.get("speed"),1)));outputs.add(out);}
      else if("VIDEO".equals(type)&&"去字幕".equals(tool)){Path out=dir.resolve("delogo.mp4");ffmpeg.run(FfmpegService.delogoCommand(local.get(0),out,intValue(cfg.get("x"),0),intValue(cfg.get("y"),0),intValue(cfg.get("w"),300),intValue(cfg.get("h"),80)));outputs.add(out);}
      else if("VIDEO".equals(type)&&"音频分离".equals(tool)){String format=Objects.toString(cfg.get("format"),"m4a");Path out=dir.resolve("audio."+format);ffmpeg.run(FfmpegService.extractAudioCommand(local.get(0),out,format));outputs.add(out);}
      else if("AUDIO".equals(type)&&"截取".equals(tool)){Path out=dir.resolve("trim.m4a");ffmpeg.run(FfmpegService.audioTrimCommand(local.get(0),out,doubleValue(cfg.get("start_sec"),0),nullableDouble(cfg.get("end_sec"))));outputs.add(out);}
      else if("AUDIO".equals(type)&&"音量调整".equals(tool)){Path out=dir.resolve("volume.m4a");ffmpeg.run(FfmpegService.audioVolumeCommand(local.get(0),out,doubleValue(cfg.get("volume"),1)));outputs.add(out);}
      else throw new ProviderException(ErrorCode.CONTENT_REJECTED,"未知 FFmpeg 工具: "+type+"/"+tool);
      List<Asset> made=new ArrayList<>();int index=0;for(Path output:outputs){Map<String,Object> one=new LinkedHashMap<>(cfg);one.put("output_index",++index);made.add(createLocalAsset(run,nr,one,output,outputType(type,tool)));}return made;
    }catch(ProviderException e){throw e;}catch(Exception e){throw new ProviderException(ErrorCode.PROVIDER_TIMEOUT,"FFmpeg 节点失败: "+e.getMessage(),e);}finally{if(dir!=null)deleteTree(dir);}
  }
  private Asset createLocalAsset(WorkflowRun run,NodeRun nr,Map<String,Object> cfg,Path file,String assetType)throws IOException{Asset a=new Asset();a.projectId=run.projectId;a.logicalKey=logicalKey(nr,cfg,assetType);a.versionNo=assets.findFirstByProjectIdAndLogicalKeyOrderByVersionNoDesc(a.projectId,a.logicalKey).map(x->x.versionNo+1).orElse(1);a.assetType=assetType;a.name=nr.nodeType+" 输出 v"+a.versionNo;a.contentStatus="draft";a.nodeRunId=nr.id;a.createdBy=run.createdBy;String name=file.getFileName().toString();String ext=name.substring(name.lastIndexOf('.')+1);a.mimeType="VIDEO".equals(assetType)?"video/"+ext:"AUDIO".equals(assetType)?"audio/"+ext:"image/"+ext;a.storageKey=storage.objectKey(run.createdBy,run.projectId,assetType,a.logicalKey,a.versionNo,ext);a.sizeBytes=storage.uploadFrom(file,a.storageKey);a.metadataJson=write(Map.of("output_index",cfg.getOrDefault("output_index",1)));return assets.save(a);}
  private void deleteTree(Path root){try(var walk=Files.walk(root)){walk.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}catch(IOException ignored){}}
  private static boolean bool(Map<String,Object> cfg,String key,boolean def){Object v=cfg.get(key);return v==null?def:v instanceof Boolean b?b:Boolean.parseBoolean(String.valueOf(v));}
  private static double doubleValue(Object v,double def){if(v instanceof Number n)return n.doubleValue();try{return v==null?def:Double.parseDouble(String.valueOf(v));}catch(Exception e){return def;}}
  private static Double nullableDouble(Object v){if(v==null||String.valueOf(v).isBlank())return null;return doubleValue(v,0);}

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
