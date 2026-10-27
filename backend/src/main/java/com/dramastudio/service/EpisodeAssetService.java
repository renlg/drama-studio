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
  return fromRun.map(n->n==episodeNumber).orElseGet(()->asset.logicalKey!=null&&asset.logicalKey.startsWith("ep"+episodeNumber+"-"));
 }
 private Optional<Integer> episodeFromRun(String id){return nodeRuns.findById(id).flatMap(this::episodeFromConfig);}
 private Optional<Integer> episodeFromConfig(NodeRun run){try{JsonNode value=json.readTree(run.configSnapshotJson).get("episode_number");if(value==null||value.isNull())return Optional.empty();return Optional.of(value.isNumber()?value.intValue():Integer.parseInt(value.asText()));}catch(Exception ignored){return Optional.empty();}}
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
