package com.dramastudio.gateway;
import java.math.BigDecimal; import java.util.*; import org.springframework.stereotype.Component; import static com.dramastudio.gateway.ProviderProtocol.*;
public final class MockCloudAdapters {
 private abstract static class Base implements Adapter {public void validateConfig(Map<String,Object> c){}public BigDecimal estimateCost(Request r){return new BigDecimal("0.01");}public Submission submit(Request r){return new Submission(UUID.randomUUID().toString(),"SUBMITTED",Map.of());}public Result query(String id){return new Result("SUCCEEDED",100,"Mock 云端生成结果",null,Map.of("mock",true));}public void cancel(String id){}public Result normalizeResult(Map<String,Object> r){return new Result(Objects.toString(r.getOrDefault("status","SUCCEEDED")),100,Objects.toString(r.getOrDefault("text","")),(String)r.get("url"),r);}}
 @Component("mockLlm") public static class Llm extends Base implements LlmProviderAdapter{}
 @Component("mockImage") public static class Image extends Base implements ImageProviderAdapter{}
 @Component("mockVideo") public static class Video extends Base implements VideoProviderAdapter{}
 private MockCloudAdapters(){}
}
