package com.dramastudio.engine;

import com.dramastudio.entity.Asset;
import com.dramastudio.entity.Project;
import com.dramastudio.repository.AssetRepository;
import com.dramastudio.repository.ProjectRepository;
import com.dramastudio.storage.OssStorageService;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 把项目设定 + 节点配置 + 上游产出组装成真实可用的提示词。
 *
 * 全云端 API 模式下，prompt 质量直接决定产出质量：项目题材/梗概与上游节点
 * （大纲/剧本/分镜）的文本都会注入下游，保证风格与剧情连贯。
 */
@Component
public class PromptBuilder {

  private final AssetRepository assets;
  private final ProjectRepository projects;
  private final OssStorageService storage;

  public PromptBuilder(AssetRepository assets, ProjectRepository projects, OssStorageService storage) {
    this.assets = assets;
    this.projects = projects;
    this.storage = storage;
  }

  /** 项目级公共设定（来自 projects 表，不再依赖节点 config）。 */
  private String projectContext(String projectId, Map<String, Object> config) {
    StringBuilder sb = new StringBuilder();
    Project p = projects.findById(projectId).orElse(null);
    String title = p != null && p.name != null ? p.name : str(config.get("title"));
    if (!title.isBlank()) sb.append("剧名《").append(title).append("》。");
    if (p != null) {
      String genres = p.genresJson;
      if (genres != null && genres.length() > 2) {
        sb.append("题材：").append(genres.replace("[", "").replace("]", "")
            .replace("\"", "")).append("。");
      }
      if (p.description != null && !p.description.isBlank()) {
        sb.append("故事梗概：").append(p.description).append("。");
      }
      if (p.storyEnvironment != null && !p.storyEnvironment.isBlank()) {
        sb.append("故事环境：").append(p.storyEnvironment).append("。");
      }
      sb.append("类型：").append(p.dramaType).append("，画风：").append(p.artStyle).append("。");
      sb.append("总集数=").append(p.episodeCount).append("，单集时长=")
          .append(p.episodeDurationSec).append("秒。");
    }
    String logline = str(config.get("logline"));
    if (!logline.isBlank()) sb.append("一句话故事：").append(logline).append("。");
    return sb.toString();
  }

  /** 取项目里某类资产的最新文本，作为上下文注入。 */
  private String latestTextOf(String projectId, String assetType) {
    return assets.findFirstByProjectIdAndAssetTypeOrderByVersionNoDesc(projectId, assetType)
        .map(a -> a.contentText == null ? "" : a.contentText)
        .orElse("");
  }

  public String build(String nodeType, Map<String, Object> config, String projectId) {
    String ctx = projectContext(projectId, config);
    return switch (nodeType) {
      case "STORY_OUTLINE" -> ctx
          + "请为以上短剧创作故事大纲。要求："
          + "分集数=" + num(config, "episode_count", 10) + "，"
          + "单集时长=" + num(config, "episode_duration_sec", 90) + "秒。"
          + "输出三部分：核心冲突（3条）、主要人物关系（4人）、"
          + "每集一句梗概+一个钩子（每条不超过25字）。"
          + "极度精简，总字数严格控制在800字以内，不要展开描写。";

      case "CHARACTER_CARD" -> {
        String outline = latestTextOf(projectId, "OUTLINE");
        yield ctx + (outline.isBlank() ? "" : "\n【故事大纲】\n" + truncate(outline, 1200))
            + "\n请设计主要角色卡片，每位角色一行字段：姓名｜年龄｜身份｜性格关键词｜外貌要点。"
            + "角色数量=" + num(config, "character_count", 4) + "。"
            + "每位不超过100字，总字数控制在800字以内。";
      }

      case "EPISODE_SCRIPT" -> {
        String outline = latestTextOf(projectId, "OUTLINE");
        String chars = latestTextOf(projectId, "CHARACTER_CARD_LIST");
        yield ctx
            + (outline.isBlank() ? "" : "\n【故事大纲】\n" + truncate(outline, 1000))
            + (chars.isBlank() ? "" : "\n【角色设定】\n" + truncate(chars, 800))
            + "\n请撰写第 " + num(config, "episode_number", 1) + " 集剧本。"
            + "时长约 " + num(config, "episode_duration_sec", 90) + " 秒。"
            + "输出：场景标题 + 对白 + 关键动作，前5秒抓住注意力，结尾留强钩子。"
            + "总字数严格控制在800字以内。";
      }

      case "EPISODE_SPLITTER" -> {
        String outline = latestTextOf(projectId, "OUTLINE");
        int total = num(config, "episode_count", 10);
        int from = num(config, "batch_start", 1);
        int to = num(config, "batch_end", total);
        yield ctx + (outline.isBlank() ? "" : "\n【大纲】\n" + truncate(outline, 1200))
            + "\n把大纲拆为第" + from + "至" + to + "集（总计" + total + "集），策略="
            + str(config.getOrDefault("split_strategy", "按大纲")) + "。"
            + "只输出严格 JSON 数组，不要 markdown。每项仅含 episode_number,title,synopsis,hook,target_duration_sec；"
            + "synopsis不超过40个汉字，hook不超过20个汉字，target_duration_sec="
            + num(config, "episode_duration_sec", 90) + "。";
      }

      case "SCENE_PROP_CARD" -> {
        String outline = latestTextOf(projectId, "OUTLINE");
        String script = latestTextOf(projectId, "EPISODE_SCRIPT");
        yield ctx + "\n【大纲】" + truncate(outline, 1000) + "\n【剧本】" + truncate(script, 1000)
            + "\n提取场景和关键道具。只输出严格JSON对象：{\"scenes\":[],\"props\":[]}。"
            + "每类最多8项，每项为不超过30字的字符串，不要解释。";
      }

      case "STORYBOARD_SCRIPT" -> {
        String script = latestTextOf(projectId, "EPISODE_SCRIPT");
        yield ctx + (script.isBlank() ? "" : "\n【本集剧本】\n" + truncate(script, 1500))
            + "\n请拆分为分镜表，每个镜头一行：镜号｜景别｜画面描述｜对白｜时长(3-6秒)。"
            + "镜头数=" + num(config, "shot_count", 20) + "。"
            + "画面描述不超过20字，总字数控制在800字以内。";
      }

      case "CHARACTER_REFERENCE_IMAGE" -> {
        String chars = latestTextOf(projectId, "CHARACTER_CARD_LIST");
        yield "角色定妆照。" + (chars.isBlank() ? "" : "\n【角色设定】\n" + truncate(chars, 1200))
            + "\n请生成该角色的四视图参考图：左至右为脸部特写、正面全身、侧面、背面。"
            + "纯白背景，写实影视质感，柔和均匀打光，无文字无水印。";
      }

      case "STORYBOARD_IMAGE" -> {
        String shots = latestTextOf(projectId, "SHOT_CARD_LIST");
        yield ctx + (shots.isBlank() ? "" : "\n【分镜表】\n" + truncate(shots, 2000))
            + "\n请按分镜表逐镜生成画面。电影感构图，写实风格，光线氛围与剧情一致，无文字无水印。";
      }

      case "SCENE_REFERENCE_IMAGE" -> ctx + "\n生成场景设定参考图。"
          + str(config.getOrDefault("background", "场景完整，无人物")) + "；"
          + str(config.getOrDefault("composition", "电影感广角构图")) + "；无文字无水印。";

      case "PROP_REFERENCE_IMAGE" -> ctx + "\n生成关键道具设定参考图。"
          + str(config.getOrDefault("background", "纯白背景")) + "；"
          + str(config.getOrDefault("composition", "产品设定图，多角度")) + "；无人物、无文字、无水印。";

      case "IMAGE_TO_VIDEO" -> {
        Object prompt = config.get("motion_prompt");
        yield (prompt == null || String.valueOf(prompt).isBlank()
                ? "镜头缓慢推进，人物微动，环境光影自然流动"
                : String.valueOf(prompt))
            + "。保持画面人物一致，无镜头切换，无文字。";
      }

      default -> ctx + nodeType;
    };
  }

  /** 显式资产引用 + 节点约定的自动引用，统一转换成供应商可下载 24 小时 OSS URL。 */
  public List<String> resolveReferenceImages(String projectId, String nodeType, Map<String, Object> config) {
    LinkedHashMap<String, Asset> resolved = new LinkedHashMap<>();
    for (String key : List.of("reference_images", "character_refs", "scene_assets", "source_image")) {
      collectRefs(config.get(key), resolved);
    }
    if (nodeType.equals("STORYBOARD_IMAGE")) {
      collectSelected(projectId, "CHARACTER_REFERENCE_IMAGE", resolved, config);
      collectSelected(projectId, "SCENE_REFERENCE_IMAGE", resolved, config);
    } else if (nodeType.equals("IMAGE_TO_VIDEO")) {
      collectSelected(projectId, "STORYBOARD_IMAGE", resolved, config);
    }
    return resolved.values().stream().filter(a -> a.storageKey != null && !a.storageKey.isBlank())
        .map(a -> storage.presignGet(a.storageKey, Duration.ofHours(24))).toList();
  }

  private void collectSelected(String projectId,String type,LinkedHashMap<String,Asset> out,Map<String,Object> config){
    int episode=num(config,"episode_number",0);
    for(Asset a:assets.findByProjectIdAndAssetTypeAndDeletedAtIsNullOrderByCreatedAtDesc(projectId,type)){
      if(!(a.selected||a.locked))continue;
      if(episode>0&&!sameEpisode(a,episode))continue;
      out.putIfAbsent(a.id,a);
    }
  }
  private boolean sameEpisode(Asset a,int episode){
    if(a.logicalKey!=null&&(a.logicalKey.contains(":"+episode+":")||a.logicalKey.endsWith(":"+episode)))return true;
    return a.metadataJson!=null&&a.metadataJson.matches("(?s).*\\\"episode_number\\\"\\s*:\\s*"+episode+"(?:\\D.*|$)");
  }
  private void collectRefs(Object value,LinkedHashMap<String,Asset> out){
    if(value==null)return;
    if(value instanceof Collection<?> c){c.forEach(x->collectRefs(x,out));return;}
    if(value instanceof Map<?,?> m){collectRefs(m.get("id"),out);return;}
    assets.findById(String.valueOf(value)).filter(a->a.deletedAt==null).ifPresent(a->out.putIfAbsent(a.id,a));
  }

  private static String truncate(String s, int n) {
    return s.length() <= n ? s : s.substring(0, n) + "…";
  }

  private static String str(Object o) {
    return o == null ? "" : String.valueOf(o);
  }

  private static int num(Map<String, Object> c, String k, int def) {
    Object v = c.get(k);
    if (v instanceof Number n) return n.intValue();
    try {
      return v == null ? def : Integer.parseInt(String.valueOf(v).trim());
    } catch (Exception e) {
      return def;
    }
  }
}
