package com.dramastudio.service;

import com.dramastudio.entity.Asset;
import com.dramastudio.repository.AssetRepository;
import com.dramastudio.storage.OssStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class NodeUploadService {
 private final AssetRepository assets; private final ProjectService projects; private final OssStorageService storage;
 private final NodeAssetService nodeAssets; private final ObjectMapper json;
 public NodeUploadService(AssetRepository a,ProjectService p,OssStorageService s,NodeAssetService n,ObjectMapper j){assets=a;projects=p;storage=s;nodeAssets=n;json=j;}

 @Transactional
 public NodeAssetService.NodeAsset upload(String projectId,String nodeId,String user,MultipartFile file,String requestedType){
  projects.getOwned(projectId,user);
  if(file==null||file.isEmpty())throw new IllegalArgumentException("请选择上传文件");
  String mime=Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
  String type=requestedType==null||requestedType.isBlank()?infer(mime):requestedType;
  String logicalKey="node-upload:"+nodeId;
  int version=assets.findFirstByProjectIdAndLogicalKeyOrderByVersionNoDesc(projectId,logicalKey).map(a->a.versionNo+1).orElse(1);
  String original=Optional.ofNullable(file.getOriginalFilename()).filter(x->!x.isBlank()).orElse("upload."+fallbackExt(mime));
  String ext=extension(original,mime), key=storage.objectKey(user,projectId,type,logicalKey,version,ext);
  Path tmp=null;
  try{
   tmp=Files.createTempFile("ds-upload-","."+ext);file.transferTo(tmp);long size=storage.uploadFrom(tmp,key);
   assets.clearSelection(projectId,logicalKey);assets.flush();
   Asset a=new Asset();a.projectId=projectId;a.logicalKey=logicalKey;a.versionNo=version;a.assetType=type;a.name=original;
   a.contentStatus="selected";a.selected=true;a.locked=false;a.storageKey=key;a.mimeType=mime;a.sizeBytes=size;a.createdBy=user;
   a.metadataJson=json.writeValueAsString(Map.of("uploaded",true,"node_id",nodeId,"file_name",original));
   return nodeAssets.toDto(assets.saveAndFlush(a));
  }catch(Exception e){if(e instanceof RuntimeException r)throw r;throw new IllegalStateException("文件上传失败: "+e.getMessage(),e);}
  finally{if(tmp!=null)try{Files.deleteIfExists(tmp);}catch(Exception ignored){}}
 }
 private static String infer(String mime){if(mime.startsWith("image/"))return "IMAGE";if(mime.startsWith("video/"))return "VIDEO";if(mime.startsWith("audio/"))return "AUDIO";return "GENERIC_OUTPUT";}
 private static String fallbackExt(String mime){if(mime.startsWith("image/"))return "png";if(mime.startsWith("video/"))return "mp4";if(mime.startsWith("audio/"))return "m4a";return "bin";}
 private static String extension(String name,String mime){int dot=name.lastIndexOf('.');if(dot>=0&&dot<name.length()-1){String x=name.substring(dot+1).toLowerCase(Locale.ROOT);if(x.matches("[a-z0-9]{1,8}"))return x;}return fallbackExt(mime);}
}
