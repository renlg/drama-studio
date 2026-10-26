package com.dramastudio.gateway;
import java.math.BigDecimal; import java.util.Map;
public final class ProviderProtocol {
 public enum ErrorCode { PROVIDER_RATE_LIMITED,PROVIDER_AUTH_FAILED,CONTENT_REJECTED,PROVIDER_TIMEOUT,QUOTA_INSUFFICIENT }
 public record Request(String model,String prompt,String negativePrompt,Map<String,Object> params,String idempotencyKey){}
 public record Submission(String taskId,String status,Map<String,Object> raw){}
 public record Result(String status,double progress,String text,String outputUrl,Map<String,Object> metadata){}
 public interface Adapter {void validateConfig(Map<String,Object> config);BigDecimal estimateCost(Request request);Submission submit(Request request);Result query(String taskId);void cancel(String taskId);Result normalizeResult(Map<String,Object> raw);}
 public interface LlmProviderAdapter extends Adapter{} public interface ImageProviderAdapter extends Adapter{} public interface VideoProviderAdapter extends Adapter{}
 private ProviderProtocol(){}
}
