package com.dramastudio.controller;
import com.dramastudio.entity.Asset; import com.dramastudio.repository.AssetRepository; import com.dramastudio.service.*; import com.dramastudio.storage.OssStorageService; import java.io.InputStream; import java.time.Duration; import java.util.*; import org.springframework.core.io.InputStreamResource; import org.springframework.http.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import org.springframework.web.multipart.MultipartFile;
@RestController @RequestMapping("/api/v1") public class AssetController {
 private final AssetService assets;private final EpisodeAssetService episodeAssets;private final NodeAssetService nodeAssets;private final NodeUploadService uploads;private final NodeGenerateService generates;private final CurrentUser current;private final OssStorageService storage;private final AssetRepository assetRepository;private final MediaUrlSigner mediaUrlSigner;public AssetController(AssetService a,EpisodeAssetService e,NodeAssetService n,NodeUploadService u,NodeGenerateService g,CurrentUser c,OssStorageService s,AssetRepository r,MediaUrlSigner m){assets=a;episodeAssets=e;nodeAssets=n;uploads=u;generates=g;current=c;storage=s;assetRepository=r;mediaUrlSigner=m;}
 @GetMapping("/projects/{id}/assets")List<Asset> list(@PathVariable String id,Authentication a){return assets.list(id,current.id(a));}
 @GetMapping("/projects/{id}/nodes/{nodeId}/assets")NodeAssetService.NodeAssets nodeAssets(@PathVariable String id,@PathVariable String nodeId,Authentication a){return nodeAssets.get(id,nodeId,current.id(a));}
 @PostMapping(value="/projects/{id}/nodes/{nodeId}/upload",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)NodeAssetService.NodeAsset nodeUpload(@PathVariable String id,@PathVariable String nodeId,@RequestPart("file")MultipartFile file,@RequestParam(required=false)String assetType,Authentication a){return uploads.upload(id,nodeId,current.id(a),file,assetType);}
 @PostMapping("/projects/{id}/nodes/{nodeId}/generate")Map<String,String> nodeGenerate(@PathVariable String id,@PathVariable String nodeId,@RequestBody GenerateRequest r,Authentication a){return Map.of("runId",generates.generate(id,nodeId,current.id(a),r.prompt,r.tool,r.params));}
 @GetMapping("/projects/{id}/episodes/{episodeNumber}/assets")EpisodeAssetService.EpisodeAssets episodeAssets(@PathVariable String id,@PathVariable int episodeNumber,Authentication a){return episodeAssets.get(id,episodeNumber,current.id(a));}
 @PostMapping("/assets/{id}/{action}")Asset action(@PathVariable String id,@PathVariable String action,Authentication a){return assets.action(id,action,current.id(a));}
 @PostMapping("/storage/upload-url")Object upload(@RequestBody UploadRequest r,Authentication a){String key=storage.objectKey(current.id(a),r.projectId,r.assetType,r.logicalKey,r.versionNo,r.extension);return Map.of("storageKey",key,"url",storage.presignPut(key,Duration.ofMinutes(5)));}
 @GetMapping("/media/{assetId}")ResponseEntity<InputStreamResource> media(@PathVariable String assetId,@RequestParam String exp,@RequestParam String sig,@RequestParam(defaultValue="")String variant){
  if(!mediaUrlSigner.verify(assetId,variant,exp,sig))return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  Optional<Asset> found=assetRepository.findById(assetId);
  if(found.isEmpty()||found.get().deletedAt!=null)return ResponseEntity.notFound().build();
  Asset asset=found.get();
  String objectKey="thumb".equals(variant)?asset.thumbnailKey:asset.storageKey;
  if(objectKey==null||objectKey.isBlank())return ResponseEntity.notFound().build();
  InputStream stream=storage.openStream(objectKey);
  MediaType contentType;
  try{contentType=MediaType.parseMediaType(asset.mimeType);}catch(Exception ignored){contentType=MediaType.APPLICATION_OCTET_STREAM;}
  return ResponseEntity.ok().contentType(contentType).cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic()).body(new InputStreamResource(stream));
 }
 public record UploadRequest(String projectId,String assetType,String logicalKey,int versionNo,String extension){}
 public record GenerateRequest(String prompt,String tool,Map<String,Object> params){}
}
