package com.dramastudio.template;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class NodeCatalogTest {
 @Test void containsExactlySixGeneralNodes(){
  assertThat(NodeCatalog.all()).containsOnlyKeys("TEXT","IMAGE","VIDEO","AUDIO","CHARACTER","SCENE");
 }
 @Test void toolsAndUnifiedPortsMatchContract(){
  assertThat(NodeCatalog.get("TEXT").params().get("tool").options()).containsExactly("生成","扩写","润色","改写","续写");
  assertThat(NodeCatalog.get("IMAGE").params().get("tool").options()).contains("文生图","图生图","抠图","裁剪","旋转","宫格切分","宫格生成");
  assertThat(NodeCatalog.get("VIDEO").params().get("tool").options()).contains("文生视频","图生视频","智能多帧","拼接","抽帧","音频分离","配音配乐");
  assertThat(NodeCatalog.get("IMAGE").inputs()).contains(new NodeCatalog.Port("chars","CHARACTER",false,true),new NodeCatalog.Port("scenes","SCENE",false,true));
 }
}
