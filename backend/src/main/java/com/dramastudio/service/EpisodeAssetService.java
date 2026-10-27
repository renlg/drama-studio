package com.dramastudio.service;

import com.dramastudio.entity.Asset;
import com.dramastudio.entity.NodeRun;
import com.dramastudio.repository.AssetRepository;
import com.dramastudio.repository.NodeRunRepository;
import com.dramastudio.storage.OssStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class EpisodeAssetService {
 private static final Set<String> SUPPORTED=Set.of("STORYBOARD_IMAGE","SHOT_VIDEO","EPISODE_SCRIPT","SHOT_CARD_LIST");
 private static final Set<String> TEXT=Set.of("EPISODE_SCRIPT","SHOT_CARD_LIST");
 /** 匹配 shot_ep01_01 / ep01 / episode_01 / ep1 中的集号 */
 private static final java.util.regex.Pattern SHOT_EP=java.util.regex.Pattern.compile("(?:^|[^a-zA-Z])ep(?:isode)?[_-]?0*(\\d{1,3})(?:[^0-9]|$)",java.util.regex.Pattern.CASE_INSENSITIVE);
 private final AssetRepository assets;private final NodeRunRepository nodeRuns;private final ProjectService projects;private final OssStorageService storage;private final ObjectMapper json;
 public EpisodeAssetService(AssetRepository a,NodeRunRepository n,ProjectService p,OssStorageService s,ObjectMapper j){assets=a;nodeRuns=n;projects=p;storage=s;json=j;}

 public EpisodeAssets get(String projectId,int episodeNumber,String user){
  projects.getOwned(projectId,user);
  Map<String,Optional<Integer>> episodeCache=new HashMap<>();
  Map<String,Asset> chosen=new LinkedHashMap<>();
  for(Asset asset:assets.findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId)){
   if(!SUPPORTED.contains(asset.assetType)||!belongsToEpisode(asset,episodeNumber,episodeCache))continue;
   Asset old=chosen.get(asset.logicalKey);
   if(old==null||better(asset,old))chosen.put(asset.logicalKey,asset);
  }
  Map<String,List<EpisodeAsset>> groups=new LinkedHashMap<>();
  for(String type:SUPPORTED)groups.put(type,new ArrayList<>());
  chosen.values().stream().sorted(Comparator.comparing((Asset a)->a.assetType).thenComparing(a->a.logicalKey)).forEach(a->groups.get(a.assetType).add(toDto(a)));
  return new EpisodeAssets(episodeNumber,groups);
 }

 private boolean belongsToEpisode(Asset asset,int episodeNumber,Map<String,Optional<Integer>> cache){
  Optional<Integer> fromRun=Optional.empty();
  if(asset.nodeRunId!=null&&!asset.nodeRunId.isBlank())fromRun=cache.computeIfAbsent(asset.nodeRunId,this::episodeFromRun);
  if(fromRun.isPresent())return fromRun.get()==episodeNumber;
  return episodeFromKey(asset.logicalKey,episodeNumber);
 }
 /** 三级回退拿集号：config.episode_number -> shot_id(shot_ep01_01) -> logical_key */
 private Optional<Integer> episodeFromRun(String id){return nodeRuns.findById(id).flatMap(this::episodeFromConfig);}
 private Optional<Integer> episodeFromConfig(NodeRun run){
  try{
   JsonNode root=json.readTree(run.configSnapshotJson);
   JsonNode value=root.get("episode_number");
   if(value!=null&&!value.isNull())return Optional.of(value.isNumber()?value.intValue():Integer.parseInt(value.asText().trim()));
   // 分镜图/图生视频节点没有 episode_number，但 shot_id 形如 shot_ep01_01，从中解析集号
   JsonNode shotId=root.get("shot_id");
   if(shotId!=null&&!shotId.isNull())return episodeFromShotId(shotId.asText());
   JsonNode episodeId=root.get("episode_id");
   if(episodeId!=null&&!episodeId.isNull())return episodeFromShotId(episodeId.asText());
   return Optional.empty();
  }catch(Exception ignored){return Optional.empty();}
 }
 /** 从 shot_ep01_01 / ep01 / episode_01 这类字符串里提取集号 */
 private static Optional<Integer> episodeFromShotId(String raw){
  if(raw==null||raw.isBlank())return Optional.empty();
  java.util.regex.Matcher m=SHOT_EP.matcher(raw);
  if(!m.find())return Optional.empty();
  try{return Optional.of(Integer.parseInt(m.group(1)));}catch(NumberFormatException ignored){return Optional.empty();}
 }
 private boolean episodeFromKey(String logicalKey,int episodeNumber){
  if(logicalKey==null||logicalKey.isBlank())return false;
  java.util.regex.Matcher m=SHOT_EP.matcher(logicalKey);
  if(m.find()){try{return Integer.parseInt(m.group(1))==episodeNumber;}catch(NumberFormatException ignored){return false;}}
  return false;
 }
 private boolean better(Asset candidate,Asset current){return candidate.selected&&!current.selected||candidate.selected==current.selected&&candidate.versionNo>current.versionNo;}
 private EpisodeAsset toDto(Asset a){
  String preview=null,thumbnail=null,play=null,content=TEXT.contains(a.assetType)?a.contentText:null;
  if("STORYBOARD_IMAGE".equals(a.assetType)){if(a.storageKey!=null)preview=storage.presignGet(a.storageKey,Duration.ofMinutes(30));if(a.thumbnailKey!=null)thumbnail=storage.presignGet(a.thumbnailKey,Duration.ofMinutes(30));}
  if("SHOT_VIDEO".equals(a.assetType)&&a.storageKey!=null)play=storage.presignGet(a.storageKey,Duration.ofHours(2));
  return new EpisodeAsset(a.id,a.assetType,a.name,a.contentStatus,a.selected,a.versionNo,preview,thumbnail,play,content);
 }
 public record EpisodeAssets(int episodeNumber,Map<String,List<EpisodeAsset>> groups){}
 public record EpisodeAsset(String id,String assetType,String name,String contentStatus,boolean isSelected,int versionNo,String previewUrl,String thumbnailUrl,String playUrl,String contentText){}
}
