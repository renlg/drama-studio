package com.dramastudio.service;

import com.dramastudio.entity.Asset;
import com.dramastudio.entity.NodeRun;
import com.dramastudio.entity.WorkflowRun;
import com.dramastudio.repository.AssetRepository;
import com.dramastudio.repository.NodeRunRepository;
import com.dramastudio.repository.WorkflowRunRepository;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * 节点级产出：查出「某个画布节点」历次运行产生的资产，供节点面板「输出」页签展示。
 *
 * <p>关联链：assets.node_run_id -> node_runs.id -> node_runs.node_id（画布节点 id）。
 * 同一 logicalKey 只保留最优版本（selected 优先，其次 versionNo 最大）。
 */
@Service
public class NodeAssetService {

 private static final Set<String> TEXT=Set.of(
     "EPISODE_SCRIPT","SHOT_CARD_LIST","PROJECT_STATE","OUTLINE","CHARACTER_CARD_LIST",
     "STORYBOARD_SCRIPT","EPISODE_OVERVIEW","PROJECT_SETUP");
 private static final Set<String> IMAGE=Set.of(
     "STORYBOARD_IMAGE","CHARACTER_REFERENCE_IMAGE","SCENE_REFERENCE_IMAGE","PROP_REFERENCE_IMAGE");

 private final AssetRepository assets;
 private final NodeRunRepository nodeRuns;
 private final WorkflowRunRepository runs;
 private final ProjectService projects;
 private final MediaUrlSigner signer;

 public NodeAssetService(AssetRepository a,NodeRunRepository n,WorkflowRunRepository r,
                         ProjectService p,MediaUrlSigner s){
  assets=a;nodeRuns=n;runs=r;projects=p;signer=s;
 }

 public NodeAssets get(String projectId,String nodeId,String user){
  projects.getOwned(projectId,user);

  // 该节点用过的最新一次运行（一次运行内同一节点只有一个 node_run）
  List<NodeRun> all=new ArrayList<>();
  for(WorkflowRun run:runs.findByProjectIdOrderByCreatedAtDesc(projectId)){
   for(NodeRun nr:nodeRuns.findByWorkflowRunIdOrderByCreatedAt(run.id)){
    if(nodeId.equals(nr.nodeId))all.add(nr);
   }
  }

  Map<String,Asset> chosen=new LinkedHashMap<>();
  for(NodeRun nr:all){
   for(Asset asset:assets.findByNodeRunIdOrderByCreatedAtDesc(nr.id)){
    Asset old=chosen.get(asset.logicalKey);
    if(old==null||better(asset,old))chosen.put(asset.logicalKey,asset);
   }
  }

  List<NodeAsset> out=new ArrayList<>();
  chosen.values().stream()
      .sorted(Comparator.comparing((Asset a)->a.assetType).thenComparing(a->a.logicalKey))
      .forEach(a->out.add(toDto(a)));
  return new NodeAssets(nodeId,out);
 }

 private boolean better(Asset candidate,Asset current){
  if(candidate.selected!=current.selected)return candidate.selected;
  return candidate.versionNo>current.versionNo;
 }

 private NodeAsset toDto(Asset a){
  String preview=null,thumbnail=null,play=null,text=null;
  boolean image=IMAGE.contains(a.assetType);
  if(image&&a.storageKey!=null)preview=signer.sign(a.id,Duration.ofDays(7).toMillis());
  if(image&&a.thumbnailKey!=null)thumbnail=signer.sign(a.id,"thumb",Duration.ofDays(7).toMillis());
  if("SHOT_VIDEO".equals(a.assetType)&&a.storageKey!=null)play=signer.sign(a.id,Duration.ofDays(7).toMillis());
  if(a.contentText!=null&&!a.contentText.isBlank())text=a.contentText;
  return new NodeAsset(a.id,a.assetType,a.name,a.logicalKey,a.contentStatus,
      a.selected,a.locked,a.versionNo,preview,thumbnail,play,text,a.createdAt);
 }

 public record NodeAssets(String nodeId,List<NodeAsset> assets){}

 public record NodeAsset(String id,String assetType,String name,String logicalKey,
                         String contentStatus,boolean isSelected,boolean isLocked,
                         int versionNo,String previewUrl,String thumbnailUrl,String playUrl,
                         String contentText,String createdAt){}
}
