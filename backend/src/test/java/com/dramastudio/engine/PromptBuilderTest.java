package com.dramastudio.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.dramastudio.entity.Project;
import com.dramastudio.repository.AssetRepository;
import com.dramastudio.repository.ProjectRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 提示词必须带上真实项目设定，否则模型会回「你未提供主题」。 */
class PromptBuilderTest {

  private PromptBuilder builder(Project p) {
    AssetRepository assets = Mockito.mock(AssetRepository.class);
    Mockito.when(assets.findFirstByProjectIdAndAssetTypeOrderByVersionNoDesc(
        Mockito.anyString(), Mockito.anyString())).thenReturn(Optional.empty());
    ProjectRepository projects = Mockito.mock(ProjectRepository.class);
    Mockito.when(projects.findById(Mockito.anyString())).thenReturn(Optional.ofNullable(p));
    return new PromptBuilder(assets, projects);
  }

  private Project sample() {
    Project p = new Project();
    p.name = "龙王归来";
    p.description = "战神归来，寿宴上递出休书";
    p.storyEnvironment = "现代都市";
    p.genresJson = "[\"都市\",\"战神归来\"]";
    p.dramaType = "动态漫";
    p.artStyle = "半写实国漫";
    p.episodeCount = 10;
    p.episodeDurationSec = 90;
    return p;
  }

  @Test
  void 大纲提示词包含项目题材与梗概() {
    String prompt = builder(sample()).build("STORY_OUTLINE", new HashMap<>(), "pid");
    assertTrue(prompt.contains("龙王归来"), "应含剧名: " + prompt);
    assertTrue(prompt.contains("战神归来"), "应含题材: " + prompt);
    assertTrue(prompt.contains("寿宴上递出休书"), "应含梗概: " + prompt);
    assertTrue(prompt.contains("总集数=10"), "应含集数: " + prompt);
  }

  @Test
  void 大纲提示词不是空壳() {
    String prompt = builder(sample()).build("STORY_OUTLINE", new HashMap<>(), "pid");
    assertTrue(prompt.length() > 80, "提示词过短，模型会回你未提供主题: " + prompt);
  }

  @Test
  void 无项目时退化到节点配置不崩溃() {
    String prompt = builder(null).build("STORY_OUTLINE",
        Map.of("title", "回退剧名"), "pid");
    assertTrue(prompt.contains("回退剧名"));
  }
}
