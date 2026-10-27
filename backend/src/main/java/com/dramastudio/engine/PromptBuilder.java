package com.dramastudio.engine;

import com.dramastudio.entity.Asset;
import com.dramastudio.repository.AssetRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 把节点配置 + 上游产出组装成真实可用的提示词。
 *
 * 全云端 API 模式下，prompt 质量直接决定产出质量：
 * 上游节点（大纲/剧本/分镜）的文本会被注入下游节点，保证风格与剧情连贯。
 */
@Component
public class PromptBuilder {

  private final AssetRepository assets;

  public PromptBuilder(AssetRepository assets) {
    this.assets = assets;
  }

  /** 项目级公共设定，注入所有节点，保证风格统一。 */
  @SuppressWarnings("unchecked")
  private String projectContext(Map<String, Object> config) {
    StringBuilder sb = new StringBuilder();
    String title = str(config.get("title"));
    if (!title.isBlank()) sb.append("剧名《").append(title).append("》。");
    Object genres = config.get("genres");
    if (genres instanceof List<?> l && !l.isEmpty()) {
      sb.append("题材：").append(l.stream().map(String::valueOf)
          .collect(Collectors.joining("、"))).append("。");
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
    String ctx = projectContext(config);
    return switch (nodeType) {
      case "STORY_OUTLINE" -> ctx
          + "请为以上短剧创作完整故事大纲。要求："
          + "分集数=" + num(config, "episode_count", 10) + "，"
          + "单集时长=" + num(config, "episode_duration_sec", 90) + "秒。"
          + "输出：核心冲突、人物关系、每集梗概与钩子。语言简洁中文。";

      case "CHARACTER_CARD" -> {
        String outline = latestTextOf(projectId, "OUTLINE");
        yield ctx + (outline.isBlank() ? "" : "\n【故事大纲】\n" + truncate(outline, 2000))
            + "\n请设计主要角色卡片。每个角色包含：姓名、年龄、身份、性格、外貌特征、人物弧光。"
            + "角色数量=" + num(config, "character_count", 4) + "。";
      }

      case "EPISODE_SCRIPT" -> {
        String outline = latestTextOf(projectId, "OUTLINE");
        String chars = latestTextOf(projectId, "CHARACTER_CARD_LIST");
        yield ctx
            + (outline.isBlank() ? "" : "\n【故事大纲】\n" + truncate(outline, 1500))
            + (chars.isBlank() ? "" : "\n【角色设定】\n" + truncate(chars, 1500))
            + "\n请撰写第 " + num(config, "episode_number", 1) + " 集完整剧本。"
            + "时长约 " + num(config, "episode_duration_sec", 90) + " 秒，"
            + "包含场景、对白、动作描写。短剧节奏：前 5 秒必须抓住注意力，结尾留强钩子。";
      }

      case "STORYBOARD_SCRIPT" -> {
        String script = latestTextOf(projectId, "EPISODE_SCRIPT");
        yield ctx + (script.isBlank() ? "" : "\n【本集剧本】\n" + truncate(script, 2500))
            + "\n请拆分为分镜表。每个镜头包含：镜号、景别、画面描述、对白、时长（3-6秒）。"
            + "镜头数=" + num(config, "shot_count", 20) + "。";
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
