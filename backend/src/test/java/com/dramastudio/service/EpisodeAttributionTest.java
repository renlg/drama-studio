package com.dramastudio.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 分集归属判定：从 shot_id / 字符串中解析集号的逻辑 */
class EpisodeAttributionTest {

  private static Optional<Integer> episodeFromShotId(String raw) throws Exception {
    Method m = EpisodeAssetService.class.getDeclaredMethod("episodeFromShotId", String.class);
    m.setAccessible(true);
    @SuppressWarnings("unchecked")
    Optional<Integer> r = (Optional<Integer>) m.invoke(null, raw);
    return r;
  }

  @Test
  void parsesShotIdFormats() throws Exception {
    assertThat(episodeFromShotId("shot_ep01_01")).contains(1);
    assertThat(episodeFromShotId("shot_ep12_03")).contains(12);
    assertThat(episodeFromShotId("ep01")).contains(1);
    assertThat(episodeFromShotId("EP3")).contains(3);
    assertThat(episodeFromShotId("episode_07")).contains(7);
    assertThat(episodeFromShotId("shot_ep100_01")).contains(100);
  }

  @Test
  void returnsEmptyForNonEpisodeStrings() throws Exception {
    assertThat(episodeFromShotId("storyboard_image:f458ab99")).isEmpty();
    assertThat(episodeFromShotId("character_card:3be1fc18")).isEmpty();
    assertThat(episodeFromShotId(null)).isEmpty();
    assertThat(episodeFromShotId("")).isEmpty();
    assertThat(episodeFromShotId("setup")).isEmpty();
  }

  @Test
  void doesNotConfuseSimilarPrefixes() throws Exception {
    // "step1" / "keep1" 里的 ep 前面是字母，不应被当成集号
    assertThat(episodeFromShotId("step1")).isEmpty();
    assertThat(episodeFromShotId("keep1")).isEmpty();
  }
}
