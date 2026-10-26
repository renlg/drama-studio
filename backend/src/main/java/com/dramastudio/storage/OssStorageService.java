package com.dramastudio.storage;
import com.aliyun.oss.*; import com.aliyun.oss.model.GeneratePresignedUrlRequest; import java.net.URL; import java.time.Duration; import java.util.*; import org.springframework.beans.factory.annotation.Value; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.web.server.ResponseStatusException;
@Service public class OssStorageService {
 /** 对象键根前缀：与 ai-connect/、bubble-pop/ 等其他项目隔离，且不被其 7 天生命周期规则命中 */
 private static final String PREFIX="drama-studio";
 @Value("${app.oss.endpoint}")String endpoint;@Value("${app.oss.bucket}")String bucket;@Value("${app.oss.access-key-id}")String key;@Value("${app.oss.access-key-secret}")String secret;
 public String objectKey(String tenant,String project,String type,String logical,int version,String ext){return String.join("/",PREFIX,safe(tenant),safe(project),safe(type),safe(logical),"v"+version,UUID.randomUUID()+"."+safe(ext));}
 public String presignGet(String objectKey,Duration ttl){return presign(objectKey,ttl,HttpMethod.GET).toString();}public String presignPut(String objectKey,Duration ttl){return presign(objectKey,ttl,HttpMethod.PUT).toString();}
 private URL presign(String objectKey,Duration ttl,HttpMethod method){if(endpoint.isBlank()||bucket.isBlank()||key.isBlank()||secret.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"OSS 尚未配置");OSS oss=new OSSClientBuilder().build(endpoint,key,secret);try{var req=new GeneratePresignedUrlRequest(bucket,objectKey,method);req.setExpiration(new Date(System.currentTimeMillis()+ttl.toMillis()));return oss.generatePresignedUrl(req);}finally{oss.shutdown();}}
 private String safe(String s){if(s==null||!s.matches("[a-zA-Z0-9._:-]+"))throw new IllegalArgumentException("非法对象键片段");return s;}
}
