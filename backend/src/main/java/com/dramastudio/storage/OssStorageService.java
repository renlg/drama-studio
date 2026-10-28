package com.dramastudio.storage;
import com.aliyun.oss.*; import com.aliyun.oss.model.GeneratePresignedUrlRequest; import java.io.*; import java.net.*; import java.net.http.*; import java.nio.file.*; import java.time.Duration; import java.util.*; import org.springframework.beans.factory.annotation.Value; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.web.server.ResponseStatusException;
@Service public class OssStorageService {
 /** 对象键根前缀：与 ai-connect/、bubble-pop/ 等其他项目隔离，且不被其 7 天生命周期规则命中 */
 private static final String PREFIX="drama-studio";
 @Value("${app.oss.endpoint}")String endpoint;@Value("${app.oss.bucket}")String bucket;@Value("${app.oss.access-key-id}")String key;@Value("${app.oss.access-key-secret}")String secret;
 public String objectKey(String tenant,String project,String type,String logical,int version,String ext){return String.join("/",PREFIX,safe(tenant),safe(project),safe(type),safe(logical),"v"+version,UUID.randomUUID()+"."+safe(ext));}
 public String presignGet(String objectKey,Duration ttl){return presign(objectKey,ttl,HttpMethod.GET).toString();}public String presignPut(String objectKey,Duration ttl){return presign(objectKey,ttl,HttpMethod.PUT).toString();}
 public long copyFromUrl(String sourceUrl,String objectKey){
  if(sourceUrl==null||sourceUrl.isBlank())throw new IllegalArgumentException("生成结果缺少远程 URL");
  if(endpoint.isBlank()||bucket.isBlank()||key.isBlank()||secret.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"OSS 尚未配置");
  try{
   HttpRequest req=HttpRequest.newBuilder(URI.create(sourceUrl)).timeout(Duration.ofMinutes(5)).GET().build();
   HttpResponse<InputStream> response=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build().send(req,HttpResponse.BodyHandlers.ofInputStream());
   if(response.statusCode()<200||response.statusCode()>=300)throw new IOException("下载生成文件失败 HTTP "+response.statusCode());
   OSS oss=new OSSClientBuilder().build(endpoint,key,secret);
   try(InputStream in=response.body()){oss.putObject(bucket,objectKey,in);return response.headers().firstValueAsLong("content-length").orElse(-1);}
   finally{oss.shutdown();}
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("下载生成文件被中断",e);}catch(Exception e){throw new IllegalStateException("生成文件落 OSS 失败: "+e.getMessage(),e);}
 }
 public void downloadTo(String objectKey,Path target){
  requireConfigured();OSS oss=new OSSClientBuilder().build(endpoint,key,secret);
  try(InputStream in=oss.getObject(bucket,objectKey).getObjectContent()){Files.copy(in,target,StandardCopyOption.REPLACE_EXISTING);}
  catch(Exception e){throw new IllegalStateException("OSS 文件下载失败: "+e.getMessage(),e);}finally{oss.shutdown();}
 }
 public InputStream openStream(String objectKey){
  requireConfigured();
  OSS oss=new OSSClientBuilder().build(endpoint,key,secret);
  try{
   InputStream content=oss.getObject(bucket,objectKey).getObjectContent();
   return new FilterInputStream(content){
    private boolean closed;
    @Override public void close()throws IOException{
     if(closed)return;
     closed=true;
     try{super.close();}finally{oss.shutdown();}
    }
   };
  }catch(Exception e){
   oss.shutdown();
   throw new IllegalStateException("OSS 读取失败: "+e.getMessage(),e);
  }
 }
 public long uploadFrom(Path source,String objectKey){
  requireConfigured();OSS oss=new OSSClientBuilder().build(endpoint,key,secret);
  try(InputStream in=Files.newInputStream(source)){oss.putObject(bucket,objectKey,in);return Files.size(source);}
  catch(Exception e){throw new IllegalStateException("FFmpeg 产物上传 OSS 失败: "+e.getMessage(),e);}finally{oss.shutdown();}
 }
 private void requireConfigured(){if(endpoint.isBlank()||bucket.isBlank()||key.isBlank()||secret.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"OSS 尚未配置");}
 private URL presign(String objectKey,Duration ttl,HttpMethod method){if(endpoint.isBlank()||bucket.isBlank()||key.isBlank()||secret.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"OSS 尚未配置");OSS oss=new OSSClientBuilder().build(endpoint,key,secret);try{var req=new GeneratePresignedUrlRequest(bucket,objectKey,method);req.setExpiration(new Date(System.currentTimeMillis()+ttl.toMillis()));return oss.generatePresignedUrl(req);}finally{oss.shutdown();}}
 private String safe(String s){if(s==null||!s.matches("[a-zA-Z0-9._:-]+"))throw new IllegalArgumentException("非法对象键片段");return s;}
}
