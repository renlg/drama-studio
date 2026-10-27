package com.dramastudio.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * ai-connect（OpenAI 兼容）真实适配器。
 *
 * 对接三个 free 模型组：
 *  - free       文本  -> POST /v1/chat/completions
 *  - image-free 图片  -> POST /v1/images/generations
 *  - video-free 视频  -> POST /v1/videos/generations （异步任务 + 轮询）
 *
 * 全部走 OpenAI 兼容协议，不自定义协议。
 */
@Configuration
public class AiConnectAdapters {

  private static final Logger log = LoggerFactory.getLogger(AiConnectAdapters.class);

  @Value("${app.aiconnect.base-url:https://ai-connect.top/v1}")
  private String baseUrl;

  @Value("${app.aiconnect.api-key:}")
  private String apiKey;

  @Value("${app.aiconnect.llm-model:hermes-token}")
  private String llmModel;

  /** 系统提示：抑制推理模型的思考发散，直出成品。 */
  private static final String SYSTEM_PROMPT =
      "你是资深短剧编剧。严格按用户要求直接输出成品内容，"
          + "不要复述需求、不要解释思路、不要输出思考过程、不要说「您未提供」。"
          + "中文输出，结构清晰，可直接用于拍摄。";

  @Value("${app.aiconnect.image-model:image-free}")
  private String imageModel;

  @Value("${app.aiconnect.video-model:video-free}")
  private String videoModel;

  private HttpClient http() {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();
  }

  private JsonNode post(String path, Object body, Duration timeout) throws Exception {
    String json = new ObjectMapper().writeValueAsString(body);
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
        .timeout(timeout)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json));
    if (apiKey != null && !apiKey.isBlank()) {
      b.header("Authorization", "Bearer " + apiKey);
    }
    HttpResponse<String> resp = http().send(b.build(), HttpResponse.BodyHandlers.ofString());
    JsonNode node = new ObjectMapper().readTree(resp.body());
    if (resp.statusCode() >= 400) {
      String msg = node.path("error").path("message").asText(resp.body());
      throw new ProviderProtocol.ProviderException(
          resp.statusCode() == 401 ? ProviderProtocol.ErrorCode.PROVIDER_AUTH_FAILED
              : resp.statusCode() == 429 ? ProviderProtocol.ErrorCode.PROVIDER_RATE_LIMITED
              : ProviderProtocol.ErrorCode.CONTENT_REJECTED,
          "ai-connect HTTP " + resp.statusCode() + ": " + msg);
    }
    return node;
  }

  private JsonNode get(String path, Duration timeout) throws Exception {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout).GET();
    if (apiKey != null && !apiKey.isBlank()) {
      b.header("Authorization", "Bearer " + apiKey);
    }
    HttpResponse<String> resp = http().send(b.build(), HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() >= 400) {
      throw new ProviderProtocol.ProviderException(
          ProviderProtocol.ErrorCode.PROVIDER_TIMEOUT,
          "ai-connect HTTP " + resp.statusCode() + ": " + resp.body());
    }
    return new ObjectMapper().readTree(resp.body());
  }

  // ---------------- 文本 ----------------

  @Bean("aiConnectLlm")
  @Primary
  public ProviderProtocol.LlmProviderAdapter aiConnectLlm() {
    return new ProviderProtocol.LlmProviderAdapter() {
      @Override
      public void validateConfig(Map<String, Object> config) {
        if (apiKey == null || apiKey.isBlank()) {
          throw new ProviderProtocol.ProviderException(
              ProviderProtocol.ErrorCode.PROVIDER_AUTH_FAILED, "未配置 ai-connect API Key");
        }
      }

      @Override
      public BigDecimal estimateCost(ProviderProtocol.Request r) {
        return BigDecimal.ZERO; // free 组不扣费
      }

      @Override
      public ProviderProtocol.Submission submit(ProviderProtocol.Request r) {
        try {
          Map<String, Object> body = new LinkedHashMap<>();
          body.put("model", llmModel);
          body.put("messages", List.of(
              Map.of("role", "system", "content", SYSTEM_PROMPT),
              Map.of("role", "user", "content", r.prompt())));
          // 推理型模型（deepseek-v4.x）会先消耗 reasoning token，
          // max_tokens 太小会导致 content 为空，因此给足额度。
          Object mt = r.params() == null ? null : r.params().get("max_tokens");
          body.put("max_tokens", mt instanceof Number ? ((Number) mt).intValue() : 8192);

          JsonNode node = post("/chat/completions", body, Duration.ofMinutes(8));
          JsonNode choice = node.path("choices").path(0);
          String text = choice.path("message").path("content").asText("");
          if (text.isBlank()) {
            // 空正文多为 reasoning 占满额度，把推理内容回传便于排查，而不是直接失败
            String reasoning = choice.path("message").path("reasoning_content").asText("");
            String finish = choice.path("finish_reason").asText("");
            throw new ProviderProtocol.ProviderException(
                ProviderProtocol.ErrorCode.CONTENT_REJECTED,
                "ai-connect 返回空内容 (finish_reason=" + finish
                    + ", usage=" + node.path("usage")
                    + ", reasoning=" + (reasoning.isBlank() ? "无" : reasoning.substring(0, Math.min(200, reasoning.length()))) + ")");
          }
          Map<String, Object> raw = new LinkedHashMap<>();
          raw.put("text", text);
          raw.put("model", node.path("model").asText(llmModel));
          raw.put("usage", new ObjectMapper().convertValue(node.path("usage"), Map.class));
          return new ProviderProtocol.Submission(
              UUID.randomUUID().toString(), "SUCCEEDED", raw);
        } catch (ProviderProtocol.ProviderException e) {
          throw e;
        } catch (Exception e) {
          throw new ProviderProtocol.ProviderException(
              ProviderProtocol.ErrorCode.PROVIDER_TIMEOUT, "文本生成失败: " + e.getMessage());
        }
      }

      @Override
      public ProviderProtocol.Result query(String taskId) {
        return new ProviderProtocol.Result("SUCCEEDED", 100, null, null, Map.of());
      }

      @Override
      public void cancel(String taskId) {
      }

      @Override
      public ProviderProtocol.Result normalizeResult(Map<String, Object> raw) {
        return new ProviderProtocol.Result(
            "SUCCEEDED", 100, Objects.toString(raw.get("text"), ""), null, raw);
      }
    };
  }

  // ---------------- 图片 ----------------

  @Bean("aiConnectImage")
  @Primary
  public ProviderProtocol.ImageProviderAdapter aiConnectImage() {
    return new ProviderProtocol.ImageProviderAdapter() {
      @Override
      public void validateConfig(Map<String, Object> config) {
        if (apiKey == null || apiKey.isBlank()) {
          throw new ProviderProtocol.ProviderException(
              ProviderProtocol.ErrorCode.PROVIDER_AUTH_FAILED, "未配置 ai-connect API Key");
        }
      }

      @Override
      public BigDecimal estimateCost(ProviderProtocol.Request r) {
        return BigDecimal.ZERO;
      }

      @Override
      public ProviderProtocol.Submission submit(ProviderProtocol.Request r) {
        try {
          Map<String, Object> body = new LinkedHashMap<>();
          body.put("model", imageModel);
          body.put("prompt", r.prompt());
          body.put("n", 1);
          String size = "1024x1024";
          if (r.params() != null) {
            Object s = r.params().get("size");
            if (s != null && !String.valueOf(s).isBlank()) size = String.valueOf(s);
          }
          body.put("size", size);

          JsonNode node = post("/images/generations", body, Duration.ofMinutes(5));
          JsonNode first = node.path("data").path(0);
          String url = first.path("url").asText(null);
          String b64 = first.path("b64_json").asText(null);
          if ((url == null || url.isBlank()) && (b64 == null || b64.isBlank())) {
            throw new ProviderProtocol.ProviderException(
                ProviderProtocol.ErrorCode.CONTENT_REJECTED, "图片生成无 url/b64 返回");
          }
          Map<String, Object> raw = new LinkedHashMap<>();
          raw.put("url", url);
          raw.put("b64", b64);
          raw.put("revisedPrompt", first.path("revised_prompt").asText(""));
          raw.put("taskId", node.path("task_id").asText(""));
          return new ProviderProtocol.Submission(
              UUID.randomUUID().toString(), "SUCCEEDED", raw);
        } catch (ProviderProtocol.ProviderException e) {
          throw e;
        } catch (Exception e) {
          throw new ProviderProtocol.ProviderException(
              ProviderProtocol.ErrorCode.PROVIDER_TIMEOUT, "图片生成失败: " + e.getMessage());
        }
      }

      @Override
      public ProviderProtocol.Result query(String taskId) {
        return new ProviderProtocol.Result("SUCCEEDED", 100, null, null, Map.of());
      }

      @Override
      public void cancel(String taskId) {
      }

      @Override
      public ProviderProtocol.Result normalizeResult(Map<String, Object> raw) {
        return new ProviderProtocol.Result(
            "SUCCEEDED", 100, null, Objects.toString(raw.get("url"), null), raw);
      }
    };
  }

  // ---------------- 视频 ----------------

  @Bean("aiConnectVideo")
  @Primary
  public ProviderProtocol.VideoProviderAdapter aiConnectVideo() {
    return new ProviderProtocol.VideoProviderAdapter() {
      @Override
      public void validateConfig(Map<String, Object> config) {
        if (apiKey == null || apiKey.isBlank()) {
          throw new ProviderProtocol.ProviderException(
              ProviderProtocol.ErrorCode.PROVIDER_AUTH_FAILED, "未配置 ai-connect API Key");
        }
      }

      @Override
      public BigDecimal estimateCost(ProviderProtocol.Request r) {
        return BigDecimal.ZERO;
      }

      @Override
      public ProviderProtocol.Submission submit(ProviderProtocol.Request r) {
        try {
          Map<String, Object> body = new LinkedHashMap<>();
          body.put("model", videoModel);
          body.put("prompt", r.prompt());
          body.put("size", "480p");
          body.put("duration", 5);

          JsonNode node = post("/videos/generations", body, Duration.ofMinutes(2));
          String id = node.path("id").asText(node.path("task_id").asText(""));
          if (id.isBlank()) {
            throw new ProviderProtocol.ProviderException(
                ProviderProtocol.ErrorCode.CONTENT_REJECTED, "视频任务无 id 返回: " + node);
          }
          Map<String, Object> raw = new LinkedHashMap<>();
          raw.put("id", id);
          return new ProviderProtocol.Submission(id, "SUBMITTED", raw);
        } catch (ProviderProtocol.ProviderException e) {
          throw e;
        } catch (Exception e) {
          throw new ProviderProtocol.ProviderException(
              ProviderProtocol.ErrorCode.PROVIDER_TIMEOUT, "视频提交失败: " + e.getMessage());
        }
      }

      @Override
      public ProviderProtocol.Result query(String taskId) {
        try {
          JsonNode node = get("/videos/" + taskId, Duration.ofSeconds(60));
          String status = node.path("status").asText(node.path("state").asText("RUNNING"));
          String normalized = switch (status.toLowerCase(Locale.ROOT)) {
            case "succeeded", "success", "completed" -> "SUCCEEDED";
            case "failed", "error" -> "FAILED";
            default -> "RUNNING";
          };
          String url = node.path("url").asText(null);
          if (url == null) url = node.path("video_url").asText(null);
          if (url == null) url = node.path("data").path(0).path("url").asText(null);
          double prog = node.path("progress").asDouble("RUNNING".equals(normalized) ? 50 : 100);
          return new ProviderProtocol.Result(normalized, prog, null, url,
              new ObjectMapper().convertValue(node, Map.class));
        } catch (Exception e) {
          return new ProviderProtocol.Result("FAILED", 0, null, null,
              Map.of("error", String.valueOf(e.getMessage())));
        }
      }

      @Override
      public void cancel(String taskId) {
        log.warn("ai-connect 视频任务不支持取消: {}", taskId);
      }

      @Override
      public ProviderProtocol.Result normalizeResult(Map<String, Object> raw) {
        String url = Objects.toString(raw.getOrDefault("url", raw.get("video_url")), null);
        return new ProviderProtocol.Result("SUCCEEDED", 100, null, url, raw);
      }
    };
  }
}
