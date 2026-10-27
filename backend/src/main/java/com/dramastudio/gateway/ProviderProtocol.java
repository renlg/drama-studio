package com.dramastudio.gateway;
import java.math.BigDecimal; import java.util.List; import java.util.Map;
public final class ProviderProtocol {
 public enum ErrorCode { PROVIDER_RATE_LIMITED,PROVIDER_AUTH_FAILED,CONTENT_REJECTED,PROVIDER_TIMEOUT,QUOTA_INSUFFICIENT }

 /** 供应商调用异常：携带可分类的 ErrorCode，供重试/降级/文案展示使用。 */
 public static class ProviderException extends RuntimeException {
  private final ErrorCode code;
  public ProviderException(ErrorCode code,String message){super(message);this.code=code;}
  public ProviderException(ErrorCode code,String message,Throwable cause){super(message,cause);this.code=code;}
  public ErrorCode code(){return code;}
 }

 public record Request(String model,String prompt,String negativePrompt,Map<String,Object> params,String idempotencyKey,List<String> referenceImageUrls){
  public Request{referenceImageUrls=referenceImageUrls==null?List.of():List.copyOf(referenceImageUrls);}
 }
 public record Submission(String taskId,String status,Map<String,Object> raw){}
 public record Result(String status,double progress,String text,String outputUrl,Map<String,Object> metadata){}
 public interface Adapter {void validateConfig(Map<String,Object> config);BigDecimal estimateCost(Request request);Submission submit(Request request);Result query(String taskId);void cancel(String taskId);Result normalizeResult(Map<String,Object> raw);}
 public interface LlmProviderAdapter extends Adapter{} public interface ImageProviderAdapter extends Adapter{} public interface VideoProviderAdapter extends Adapter{}
 private ProviderProtocol(){}
}
