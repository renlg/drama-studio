package com.dramastudio.engine;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class WorkflowOutputTypeTest {
 @Test void mapsGeneralOutputs(){
  assertThat(WorkflowExecutor.outputType("TEXT_GEN")).isEqualTo("TEXT");
  assertThat(WorkflowExecutor.outputType("IMAGE_GEN")).isEqualTo("STORYBOARD_IMAGE");
  assertThat(WorkflowExecutor.outputType("VIDEO_GEN")).isEqualTo("SHOT_VIDEO");
  assertThat(WorkflowExecutor.outputType("VIDEO_MULTI_KEYFRAME")).isEqualTo("SHOT_VIDEO");
  assertThat(WorkflowExecutor.outputType("VIDEO_CONCAT")).isEqualTo("SHOT_VIDEO");
  assertThat(WorkflowExecutor.outputType("MEDIA_TRIM")).isEqualTo("SHOT_VIDEO");
  assertThat(WorkflowExecutor.outputType("VIDEO_MUX_AUDIO")).isEqualTo("SHOT_VIDEO");
  assertThat(WorkflowExecutor.outputType("MEDIA_SCALE")).isEqualTo("SHOT_VIDEO");
  assertThat(WorkflowExecutor.outputType("MEDIA_EXTRACT_FRAME")).isEqualTo("STORYBOARD_IMAGE");
 }
}
