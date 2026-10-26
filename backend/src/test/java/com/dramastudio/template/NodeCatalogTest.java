package com.dramastudio.template;
import org.junit.jupiter.api.Test; import static org.assertj.core.api.Assertions.*;
class NodeCatalogTest { @Test void containsAllTenNodeTypesAndCommonParams(){assertThat(NodeCatalog.all()).hasSize(10);assertThat(NodeCatalog.get("IMAGE_TO_VIDEO").params()).containsKeys("source_image","duration_sec","provider","timeout_sec","retry_count");assertThat(NodeCatalog.get("PROJECT_SETUP").params()).containsKeys("story_environment","genre","temperature","max_tokens");} }
