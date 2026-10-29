package com.dramastudio.engine;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class WorkflowOutputTypeTest {
 @Test void mapsSixNodeOutputsAndVideoSpecialCases(){
  assertThat(WorkflowExecutor.outputType("TEXT","生成")).isEqualTo("TEXT");
  assertThat(WorkflowExecutor.outputType("IMAGE","抠图")).isEqualTo("IMAGE");
  assertThat(WorkflowExecutor.outputType("VIDEO","文生视频")).isEqualTo("VIDEO");
  assertThat(WorkflowExecutor.outputType("VIDEO","图生视频")).isEqualTo("VIDEO");
  assertThat(WorkflowExecutor.outputType("AUDIO","截取")).isEqualTo("AUDIO");
 }
 @Test void resolvesRuntimeCapabilities(){
  assertThat(WorkflowExecutor.effectiveCapability("TEXT",Map.of("tool","润色"))).isEqualTo("LLM");
  assertThat(WorkflowExecutor.effectiveCapability("IMAGE",Map.of("tool","裁剪"))).isEqualTo("FFMPEG");
  assertThat(WorkflowExecutor.effectiveCapability("VIDEO",Map.of("tool","图生视频"))).isEqualTo("VIDEO");
  assertThat(WorkflowExecutor.effectiveCapability("AUDIO",Map.of("tool","上传"))).isEqualTo("NONE");
 }
}
