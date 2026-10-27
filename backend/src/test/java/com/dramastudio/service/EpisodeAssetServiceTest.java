package com.dramastudio.service;

import com.dramastudio.entity.*;
import com.dramastudio.repository.*;
import com.dramastudio.storage.OssStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EpisodeAssetServiceTest {
 @Test void aggregatesEpisodeAndChoosesSelectedOrLatestVersion(){
  AssetRepository assets=mock(AssetRepository.class);NodeRunRepository runs=mock(NodeRunRepository.class);ProjectService projects=mock(ProjectService.class);OssStorageService storage=mock(OssStorageService.class);
  when(storage.presignGet(anyString(),any(Duration.class))).thenAnswer(i->"signed:"+i.getArgument(0));
  NodeRun ep2=run("run-2","{\"episode_number\":2}"),noEpisode=run("run-none","{}");when(runs.findById("run-2")).thenReturn(Optional.of(ep2));when(runs.findById("run-none")).thenReturn(Optional.of(noEpisode));
  Asset old=image("old","shot-a",1,false,"run-2"),selected=image("selected","shot-a",2,true,"run-2"),newer=image("newer","shot-a",3,false,"run-2");
  Asset fallback=text("text","ep2-script",1,"run-none"),wrong=text("wrong","ep3-script",1,"run-none");
  when(assets.findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc("project")).thenReturn(List.of(old,selected,newer,fallback,wrong));
  var result=new EpisodeAssetService(assets,runs,projects,storage,new ObjectMapper()).get("project",2,"user");
  assertThat(result.groups().get("STORYBOARD_IMAGE")).extracting(EpisodeAssetService.EpisodeAsset::id).containsExactly("selected");
  assertThat(result.groups().get("STORYBOARD_IMAGE").get(0).previewUrl()).isEqualTo("signed:image-key");
  assertThat(result.groups().get("EPISODE_SCRIPT")).extracting(EpisodeAssetService.EpisodeAsset::contentText).containsExactly("episode text");
  verify(projects).getOwned("project","user");
 }
 private NodeRun run(String id,String config){NodeRun r=new NodeRun();r.id=id;r.configSnapshotJson=config;return r;}
 private Asset image(String id,String key,int version,boolean selected,String run){Asset a=base(id,"STORYBOARD_IMAGE",key,version,run);a.selected=selected;a.storageKey="image-key";a.thumbnailKey="thumb-key";return a;}
 private Asset text(String id,String key,int version,String run){Asset a=base(id,"EPISODE_SCRIPT",key,version,run);a.contentText="episode text";return a;}
 private Asset base(String id,String type,String key,int version,String run){Asset a=new Asset();a.id=id;a.assetType=type;a.logicalKey=key;a.versionNo=version;a.nodeRunId=run;a.name=id;a.contentStatus="selected";return a;}
}
