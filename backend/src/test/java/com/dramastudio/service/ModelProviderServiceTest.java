package com.dramastudio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dramastudio.entity.ModelProvider;
import com.dramastudio.entity.ProviderModel;
import com.dramastudio.repository.ModelProviderRepository;
import com.dramastudio.repository.ProviderModelRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 供应商/模型配置的核心不变量：密钥不外泄、默认唯一、分类合法、回落链路。 */
class ModelProviderServiceTest {
  private ModelProviderRepository providers;
  private ProviderModelRepository models;
  private ModelProviderService service;

  @BeforeEach
  void setUp() {
    providers = Mockito.mock(ModelProviderRepository.class);
    models = Mockito.mock(ProviderModelRepository.class);
    service = new ModelProviderService(providers, models, "test-enc-key-please-change");
  }

  private ModelProvider provider(String id, String name, boolean def) {
    ModelProvider p = new ModelProvider();
    p.id = id; p.name = name; p.baseUrl = "https://api.example.com/v1";
    p.isDefault = def ? 1 : 0; p.enabled = 1; p.sortOrder = 0;
    return p;
  }

  private ProviderModel model(String rowId, String pid, String cat, String modelId, boolean def) {
    ProviderModel m = new ProviderModel();
    m.id = rowId; m.providerId = pid; m.category = cat; m.modelId = modelId;
    m.displayName = modelId; m.isDefault = def ? 1 : 0; m.enabled = 1; m.sortOrder = 0;
    return m;
  }

  // ---------- 密钥安全 ----------

  @Test
  void apiKeyIsEncryptedAtRestAndNeverReturnedInDto() {
    Mockito.when(providers.findAllByOrderBySortOrderAscCreatedAtAsc()).thenReturn(List.of());
    Mockito.when(providers.findByEnabledOrderBySortOrderAsc(1)).thenReturn(List.of());
    Mockito.when(models.findAll()).thenReturn(List.of());
    Mockito.when(providers.saveAndFlush(Mockito.any())).thenAnswer(inv -> {
      ModelProvider p = inv.getArgument(0);
      if (p.id == null) p.id = "p1";
      return p;
    });

    var dto = service.create("火山", "https://api.example.com/v1", "sk-super-secret-value", true, true, 0);

    ArgumentCaptor<ModelProvider> captor = ArgumentCaptor.forClass(ModelProvider.class);
    Mockito.verify(providers).saveAndFlush(captor.capture());
    String stored = captor.getValue().apiKeyEnc;

    assertThat(stored).isNotNull();
    assertThat(stored).doesNotContain("sk-super-secret-value");
    assertThat(stored).startsWith("gcm:");

    // DTO 只暴露掩码，不能有明文和密文
    assertThat(dto.toString()).doesNotContain("sk-super-secret-value");
    assertThat(dto.get("hasKey")).isEqualTo(true);
    assertThat(String.valueOf(dto.get("keyMask"))).isEqualTo("sk-s****alue");
  }

  @Test
  void maskShortKeyCompletely() {
    assertThat(ModelProviderService.mask("abc")).isEqualTo("****");
    assertThat(ModelProviderService.mask("abcdefgh")).isEqualTo("****");
    assertThat(ModelProviderService.mask("abcdefghij")).isEqualTo("abcd****ghij");
    assertThat(ModelProviderService.mask(null)).isEmpty();
  }

  @Test
  void encryptDecryptRoundTrip() {
    String plain = "sk-round-trip-1234567890";
    String enc = service.encrypt(plain);
    assertThat(enc).startsWith("gcm:").doesNotContain(plain);
    assertThat(service.decrypt(enc)).isEqualTo(plain);
  }

  @Test
  void differentCiphertextForSamePlaintext() {
    String plain = "same-key";
    assertThat(service.encrypt(plain)).isNotEqualTo(service.encrypt(plain));
  }

  // ---------- 分类校验 ----------

  @Test
  void rejectsUnknownCategory() {
    Mockito.when(providers.findById("p1")).thenReturn(Optional.of(provider("p1", "A", true)));
    assertThatThrownBy(() -> service.addModel("p1", "MUSIC", "m", "M", false, true, 0, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("分类");
  }

  @Test
  void acceptsAllFourCategories() {
    Mockito.when(providers.findById("p1")).thenReturn(Optional.of(provider("p1", "A", true)));
    Mockito.when(models.findByProviderIdAndCategoryOrderBySortOrderAsc(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(List.of());
    Mockito.when(models.saveAndFlush(Mockito.any())).thenAnswer(inv -> {
      ProviderModel m = inv.getArgument(0);
      if (m.id == null) m.id = "row";
      return m;
    });
    for (String cat : List.of("TEXT", "IMAGE", "VIDEO", "AUDIO")) {
      assertThat(service.addModel("p1", cat, "mid-" + cat, "N", false, true, 0, null))
          .containsEntry("category", cat);
    }
  }

  @Test
  void firstModelOfCategoryBecomesDefault() {
    Mockito.when(providers.findById("p1")).thenReturn(Optional.of(provider("p1", "A", true)));
    Mockito.when(models.findByProviderIdAndCategoryOrderBySortOrderAsc("p1", "IMAGE")).thenReturn(List.of());
    Mockito.when(models.saveAndFlush(Mockito.any())).thenAnswer(inv -> {
      ProviderModel m = inv.getArgument(0);
      if (m.id == null) m.id = "row";
      return m;
    });
    var dto = service.addModel("p1", "IMAGE", "img-1", "第一个", false, true, 0, null);
    assertThat(dto.get("isDefault")).isEqualTo(true);
  }

  // ---------- 解析回落链路 ----------

  @Test
  void resolveFallsBackToDefaultProviderAndDefaultModel() {
    ModelProvider def = provider("p-def", "默认供应商", true);
    Mockito.when(providers.findById(Mockito.anyString())).thenReturn(Optional.empty());
    Mockito.when(providers.findFirstByIsDefaultOrderBySortOrderAsc(1)).thenReturn(Optional.of(def));
    Mockito.when(models.findFirstByProviderIdAndCategoryAndIsDefaultOrderBySortOrderAsc("p-def", "VIDEO", 1))
        .thenReturn(Optional.of(model("r1", "p-def", "VIDEO", "default-video-model", true)));

    var r = service.resolve(null, "VIDEO", null);
    assertThat(r).isNotNull();
    assertThat(r.providerId()).isEqualTo("p-def");
    assertThat(r.modelId()).isEqualTo("default-video-model");
    assertThat(r.baseUrl()).isEqualTo("https://api.example.com/v1");
  }

  @Test
  void resolveHonoursExplicitProviderAndModel() {
    ModelProvider p = provider("p-x", "指定供应商", false);
    Mockito.when(providers.findById("p-x")).thenReturn(Optional.of(p));
    Mockito.when(models.findFirstByProviderIdAndCategoryAndModelId("p-x", "IMAGE", "seedream-pro"))
        .thenReturn(Optional.of(model("r9", "p-x", "IMAGE", "seedream-pro", false)));

    var r = service.resolve("p-x", "IMAGE", "seedream-pro");
    assertThat(r.providerId()).isEqualTo("p-x");
    assertThat(r.modelId()).isEqualTo("seedream-pro");
  }

  @Test
  void resolveReturnsNullWhenNoProviderConfigured() {
    Mockito.when(providers.findById(Mockito.anyString())).thenReturn(Optional.empty());
    Mockito.when(providers.findFirstByIsDefaultOrderBySortOrderAsc(1)).thenReturn(Optional.empty());
    Mockito.when(providers.findByEnabledOrderBySortOrderAsc(1)).thenReturn(List.of());
    assertThat(service.resolve("nope", "IMAGE", null)).isNull();
  }
}
