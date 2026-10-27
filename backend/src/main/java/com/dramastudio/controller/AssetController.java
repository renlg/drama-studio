package com.dramastudio.controller;
import com.dramastudio.entity.Asset; import com.dramastudio.service.*; import com.dramastudio.storage.OssStorageService; import java.time.Duration; import java.util.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1") public class AssetController {
 private final AssetService assets;private final EpisodeAssetService episodeAssets;private final NodeAssetService nodeAssets;private final CurrentUser current;private final OssStorageService storage;public AssetController(AssetService a,EpisodeAssetService e,NodeAssetService n,CurrentUser c,OssStorageService s){assets=a;episodeAssets=e;nodeAssets=n;current=c;storage=s;}
 @GetMapping("/projects/{id}/assets")List<Asset> list(@PathVariable String id,Authentication a){return assets.list(id,current.id(a));}
 @GetMapping("/projects/{id}/nodes/{nodeId}/assets")NodeAssetService.NodeAssets nodeAssets(@PathVariable String id,@PathVariable String nodeId,Authentication a){return nodeAssets.get(id,nodeId,current.id(a));}
 @GetMapping("/projects/{id}/episodes/{episodeNumber}/assets")EpisodeAssetService.EpisodeAssets episodeAssets(@PathVariable String id,@PathVariable int episodeNumber,Authentication a){return episodeAssets.get(id,episodeNumber,current.id(a));}
 @PostMapping("/assets/{id}/{action}")Asset action(@PathVariable String id,@PathVariable String action,Authentication a){return assets.action(id,action,current.id(a));}
 @PostMapping("/storage/upload-url")Object upload(@RequestBody UploadRequest r,Authentication a){String key=storage.objectKey(current.id(a),r.projectId,r.assetType,r.logicalKey,r.versionNo,r.extension);return Map.of("storageKey",key,"url",storage.presignPut(key,Duration.ofMinutes(5)));}
 public record UploadRequest(String projectId,String assetType,String logicalKey,int versionNo,String extension){}
}
