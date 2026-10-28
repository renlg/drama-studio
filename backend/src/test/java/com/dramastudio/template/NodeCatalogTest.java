package com.dramastudio.template;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class NodeCatalogTest {
 @Test void containsExistingAndCommonParams(){assertThat(NodeCatalog.all()).hasSize(27);assertThat(NodeCatalog.get("IMAGE_TO_VIDEO").params()).containsKeys("source_image","duration_sec","provider","timeout_sec","retry_count","mode");assertThat(NodeCatalog.get("PROJECT_SETUP").params()).containsKeys("story_environment","genre","temperature","max_tokens");assertThat(NodeCatalog.get("EPISODE_OVERVIEW").params()).containsKeys("episode_number","auto_refresh");assertThat(NodeCatalog.get("EPISODE_SPLITTER").outputs().get(0).type()).isEqualTo("EPISODE_LIST");}
 @Test void containsGeneralCreationNodesWithPorts(){
  assertPort("TEXT_GEN","text","TEXT",false,true,"text","TEXT");
  assertPort("IMAGE_GEN","ref_images","ASSET_REF_LIST",false,true,"images","IMAGE_ASSET_LIST");
  assertPort("VIDEO_GEN","images","IMAGE_ASSET_LIST",false,true,"videos","VIDEO_ASSET_LIST");
  assertPort("VIDEO_MULTI_KEYFRAME","images","IMAGE_ASSET_LIST",true,true,"videos","VIDEO_ASSET_LIST");
 }
 @Test void containsEditingNodesWithPorts(){
  assertPort("VIDEO_CONCAT","videos","VIDEO_ASSET_LIST",true,true,"video","VIDEO_ASSET_LIST");
  assertPort("MEDIA_TRIM","media","VIDEO_ASSET_LIST",true,false,"video","VIDEO_ASSET_LIST");
  assertPort("MEDIA_SCALE","media","VIDEO_ASSET_LIST",true,false,"video","VIDEO_ASSET_LIST");
  assertPort("MEDIA_EXTRACT_FRAME","video","VIDEO_ASSET_LIST",true,false,"images","IMAGE_ASSET_LIST");
  assertThat(NodeCatalog.get("VIDEO_MUX_AUDIO").inputs()).extracting(NodeCatalog.Port::name).containsExactly("video","audio");
  assertThat(NodeCatalog.get("IMAGE_CHROMA_KEY").capability()).isEqualTo("FFMPEG");
  assertThat(NodeCatalog.get("VIDEO_INTERPOLATE").capability()).isEqualTo("FFMPEG");
 }
 private void assertPort(String type,String input,String inputType,boolean required,boolean multiple,String output,String outputType){var d=NodeCatalog.get(type);assertThat(d).isNotNull();assertThat(d.inputs()).contains(new NodeCatalog.Port(input,inputType,required,multiple));assertThat(d.outputs()).extracting(NodeCatalog.Port::name,NodeCatalog.Port::type).contains(tuple(output,outputType));}
}
