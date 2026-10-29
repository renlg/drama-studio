package com.dramastudio.service;

import com.dramastudio.entity.ModelProvider;
import com.dramastudio.entity.ProviderModel;
import com.dramastudio.repository.ModelProviderRepository;
import com.dramastudio.repository.ProviderModelRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模型供应商配置。
 *
 * 密钥安全约定：
 *  - 写库前用 AES-GCM 加密（密钥来自 app.provider-enc-key / PROVIDER_ENC_KEY）
 *  - 对外 API 永不返回明文，只返回 hasKey + 掩码（前4 + **** + 后4）
 *  - 未配置加密密钥时退化为明文存储并告警（仅便于本地开发），但仍不对外返回明文
 */
@Service
public class ModelProviderService {
  private static final Logger log = LoggerFactory.getLogger(ModelProviderService.class);
  private static final String CATS = "TEXT,IMAGE,VIDEO,AUDIO";
  private static final int GCM_IV = 12, GCM_TAG = 128;
  private final ModelProviderRepository providers;
  private final ProviderModelRepository models;
  private final String encKey;
  private final SecureRandom rnd = new SecureRandom();

  public ModelProviderService(ModelProviderRepository p, ProviderModelRepository m,
      @Value("${app.provider-enc-key:${PROVIDER_ENC_KEY:}}") String encKey) {
    this.providers = p; this.models = m; this.encKey = encKey == null ? "" : encKey.trim();
    if (this.encKey.isEmpty()) log.warn("PROVIDER_ENC_KEY 未配置，供应商密钥将以明文存储（不影响功能，建议生产环境配置）");
  }

  // ---------- 查询 ----------

  public List<Map<String, Object>> list(boolean onlyEnabled) {
    List<ModelProvider> ps = onlyEnabled
        ? providers.findByEnabledOrderBySortOrderAsc(1)
        : providers.findAllByOrderBySortOrderAscCreatedAtAsc();
    List<ProviderModel> all = onlyEnabled
        ? models.findByEnabledOrderByProviderIdAscCategoryAscSortOrderAsc(1)
        : models.findAll();
    Map<String, List<ProviderModel>> byProvider = new HashMap<>();
    for (ProviderModel m : all) byProvider.computeIfAbsent(m.providerId, k -> new ArrayList<>()).add(m);
    List<Map<String, Object>> out = new ArrayList<>();
    for (ModelProvider p : ps) out.add(toDto(p, byProvider.getOrDefault(p.id, List.of())));
    return out;
  }

  public List<Map<String, Object>> modelsOf(String providerId, String category, boolean onlyEnabled) {
    List<ProviderModel> list = category == null || category.isBlank()
        ? models.findByProviderIdOrderByCategoryAscSortOrderAsc(providerId)
        : models.findByProviderIdAndCategoryOrderBySortOrderAsc(providerId, category);
    List<Map<String, Object>> out = new ArrayList<>();
    for (ProviderModel m : list) {
      if (onlyEnabled && !isOn(m.enabled)) continue;
      out.add(modelDto(m));
    }
    return out;
  }

  /** 解析生效模型：无 providerId 时用默认供应商；无 modelId 时用该分类默认模型。 */
  public Resolved resolve(String providerId, String category, String modelId) {
    ModelProvider p = null;
    if (providerId != null && !providerId.isBlank()) p = providers.findById(providerId).orElse(null);
    if (p == null) p = providers.findFirstByIsDefaultOrderBySortOrderAsc(1).orElse(null);
    if (p == null) {
      List<ModelProvider> all = providers.findByEnabledOrderBySortOrderAsc(1);
      p = all.isEmpty() ? null : all.get(0);
    }
    if (p == null) return null;
    ProviderModel m = null;
    if (modelId != null && !modelId.isBlank())
      m = models.findFirstByProviderIdAndCategoryAndModelId(p.id, category, modelId).orElse(null);
    if (m == null)
      m = models.findFirstByProviderIdAndCategoryAndIsDefaultOrderBySortOrderAsc(p.id, category, 1).orElse(null);
    if (m == null) {
      List<ProviderModel> list = models.findByProviderIdAndCategoryOrderBySortOrderAsc(p.id, category);
      m = list.isEmpty() ? null : list.get(0);
    }
    String key = decrypt(p.apiKeyEnc);
    return new Resolved(p.id, p.name, p.baseUrl, key, m == null ? null : m.modelId,
        m == null ? null : m.displayName);
  }

  public record Resolved(String providerId, String providerName, String baseUrl, String apiKey,
      String modelId, String displayName) {}

  // ---------- 供应商写操作 ----------

  @Transactional
  public Map<String, Object> create(String name, String baseUrl, String apiKey, boolean makeDefault, Boolean enabled, Integer sortOrder) {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("供应商名称不能为空");
    if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("Base URL 不能为空");
    ModelProvider p = new ModelProvider();
    p.name = name.trim(); p.baseUrl = baseUrl.trim();
    p.apiKeyEnc = encrypt(apiKey);
    p.isDefault = makeDefault ? 1 : 0;
    p.enabled = enabled == null || enabled ? 1 : 0;
    p.sortOrder = sortOrder == null ? 0 : sortOrder;
    if (makeDefault) providers.findAllByOrderBySortOrderAscCreatedAtAsc().forEach(x -> x.isDefault = 0);
    providers.saveAndFlush(p);
    return toDto(p, List.of());
  }

  @Transactional
  public Map<String, Object> update(String id, String name, String baseUrl, String apiKey, Boolean enabled, Integer sortOrder) {
    ModelProvider p = providers.findById(id).orElseThrow(() -> new IllegalArgumentException("供应商不存在"));
    if (name != null && !name.isBlank()) p.name = name.trim();
    if (baseUrl != null && !baseUrl.isBlank()) p.baseUrl = baseUrl.trim();
    // 只有显式传入非空 apiKey 才覆盖；传空字符串表示"不修改"
    if (apiKey != null && !apiKey.isBlank()) p.apiKeyEnc = encrypt(apiKey);
    if (enabled != null) p.enabled = enabled ? 1 : 0;
    if (sortOrder != null) p.sortOrder = sortOrder;
    providers.saveAndFlush(p);
    return toDto(p, models.findByProviderIdOrderByCategoryAscSortOrderAsc(p.id));
  }

  @Transactional
  public void delete(String id) {
    if (!providers.existsById(id)) throw new IllegalArgumentException("供应商不存在");
    models.deleteAll(models.findByProviderIdOrderByCategoryAscSortOrderAsc(id));
    providers.deleteById(id);
  }

  @Transactional
  public void setDefault(String id) {
    ModelProvider p = providers.findById(id).orElseThrow(() -> new IllegalArgumentException("供应商不存在"));
    providers.findAllByOrderBySortOrderAscCreatedAtAsc().forEach(x -> x.isDefault = 0);
    p.isDefault = 1;
    providers.saveAndFlush(p);
  }

  // ---------- 模型写操作 ----------

  @Transactional
  public Map<String, Object> addModel(String providerId, String category, String modelId, String displayName,
      Boolean makeDefault, Boolean enabled, Integer sortOrder, String extraJson) {
    providers.findById(providerId).orElseThrow(() -> new IllegalArgumentException("供应商不存在"));
    String cat = normCategory(category);
    if (modelId == null || modelId.isBlank()) throw new IllegalArgumentException("模型 ID 不能为空");
    boolean on = enabled == null || enabled;
    boolean def = makeDefault != null && makeDefault;
    if (def) models.clearDefaultForCategory(providerId, cat);
    ProviderModel m = new ProviderModel();
    m.providerId = providerId; m.category = cat; m.modelId = modelId.trim();
    m.displayName = displayName == null || displayName.isBlank() ? modelId.trim() : displayName.trim();
    m.isDefault = def ? 1 : 0; m.enabled = on ? 1 : 0;
    m.sortOrder = sortOrder == null ? 0 : sortOrder;
    m.extraJson = extraJson;
    // 该分类第一个模型自动成默认，避免出现"无默认"空态
    if (!def && models.findByProviderIdAndCategoryOrderBySortOrderAsc(providerId, cat).isEmpty()) m.isDefault = 1;
    return modelDto(models.saveAndFlush(m));
  }

  @Transactional
  public Map<String, Object> updateModel(String providerId, String modelRowId, String modelId, String displayName,
      Boolean enabled, Integer sortOrder, String extraJson) {
    ProviderModel m = models.findById(modelRowId).orElseThrow(() -> new IllegalArgumentException("模型不存在"));
    if (!Objects.equals(m.providerId, providerId)) throw new IllegalArgumentException("模型不属于该供应商");
    if (modelId != null && !modelId.isBlank()) m.modelId = modelId.trim();
    if (displayName != null && !displayName.isBlank()) m.displayName = displayName.trim();
    if (enabled != null) m.enabled = enabled ? 1 : 0;
    if (sortOrder != null) m.sortOrder = sortOrder;
    if (extraJson != null) m.extraJson = extraJson;
    return modelDto(models.saveAndFlush(m));
  }

  @Transactional
  public void deleteModel(String providerId, String modelRowId) {
    ProviderModel m = models.findById(modelRowId).orElseThrow(() -> new IllegalArgumentException("模型不存在"));
    if (!Objects.equals(m.providerId, providerId)) throw new IllegalArgumentException("模型不属于该供应商");
    models.delete(m);
  }

  @Transactional
  public void setDefaultModel(String providerId, String modelRowId) {
    ProviderModel m = models.findById(modelRowId).orElseThrow(() -> new IllegalArgumentException("模型不存在"));
    if (!Objects.equals(m.providerId, providerId)) throw new IllegalArgumentException("模型不属于该供应商");
    models.clearDefaultForCategory(providerId, m.category);
    models.flush();
    m.isDefault = 1;
    models.saveAndFlush(m);
  }

  // ---------- 内部 ----------

  private static boolean isOn(Integer v) { return v != null && v == 1; }

  private static String normCategory(String c) {
    String up = c == null ? "" : c.trim().toUpperCase(Locale.ROOT);
    if (!List.of(CATS.split(",")).contains(up)) throw new IllegalArgumentException("分类必须是 TEXT/IMAGE/VIDEO/AUDIO");
    return up;
  }

  private Map<String, Object> toDto(ModelProvider p, List<ProviderModel> ms) {
    Map<String, Object> dto = new LinkedHashMap<>();
    dto.put("id", p.id);
    dto.put("name", p.name);
    dto.put("baseUrl", p.baseUrl);
    dto.put("isDefault", isOn(p.isDefault));
    dto.put("enabled", isOn(p.enabled));
    dto.put("sortOrder", p.sortOrder == null ? 0 : p.sortOrder);
    String plain = decrypt(p.apiKeyEnc);
    dto.put("hasKey", plain != null && !plain.isEmpty());
    dto.put("keyMask", mask(plain));
    Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
    for (String c : CATS.split(",")) grouped.put(c, new ArrayList<>());
    for (ProviderModel m : ms) grouped.computeIfAbsent(m.category, k -> new ArrayList<>()).add(modelDto(m));
    dto.put("models", grouped);
    dto.put("modelCount", ms.size());
    return dto;
  }

  private Map<String, Object> modelDto(ProviderModel m) {
    Map<String, Object> dto = new LinkedHashMap<>();
    dto.put("id", m.id);
    dto.put("providerId", m.providerId);
    dto.put("category", m.category);
    dto.put("modelId", m.modelId);
    dto.put("displayName", m.displayName);
    dto.put("isDefault", isOn(m.isDefault));
    dto.put("enabled", isOn(m.enabled));
    dto.put("sortOrder", m.sortOrder == null ? 0 : m.sortOrder);
    dto.put("extraJson", m.extraJson);
    return dto;
  }

  /** 掩码：前4 + **** + 后4；过短则整体星号。 */
  static String mask(String key) {
    if (key == null || key.isEmpty()) return "";
    int n = key.length();
    if (n <= 8) return "****";
    return key.substring(0, 4) + "****" + key.substring(n - 4);
  }

  // ---------- 加解密 ----------

  String encrypt(String plain) {
    if (plain == null || plain.isEmpty()) return null;
    if (encKey.isEmpty()) return "plain:" + plain;
    try {
      byte[] iv = new byte[GCM_IV];
      rnd.nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG, iv));
      byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      byte[] out = new byte[iv.length + ct.length];
      System.arraycopy(iv, 0, out, 0, iv.length);
      System.arraycopy(ct, 0, out, iv.length, ct.length);
      return "gcm:" + Base64.getEncoder().encodeToString(out);
    } catch (Exception e) {
      throw new IllegalStateException("密钥加密失败", e);
    }
  }

  String decrypt(String stored) {
    if (stored == null || stored.isEmpty()) return null;
    if (stored.startsWith("plain:")) return stored.substring(6);
    if (!stored.startsWith("gcm:")) return stored;
    if (encKey.isEmpty()) {
      log.warn("存在加密密钥但 PROVIDER_ENC_KEY 未配置，无法解密该供应商密钥");
      return null;
    }
    try {
      byte[] all = Base64.getDecoder().decode(stored.substring(4));
      byte[] iv = Arrays.copyOfRange(all, 0, GCM_IV);
      byte[] ct = Arrays.copyOfRange(all, GCM_IV, all.length);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG, iv));
      return new String(c.doFinal(ct), StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.warn("供应商密钥解密失败: {}", e.getMessage());
      return null;
    }
  }

  private SecretKeySpec key() throws Exception {
    // 允许任意长度口令：SHA-256 派生 32 字节 AES-256 key
    byte[] k = MessageDigest.getInstance("SHA-256").digest(encKey.getBytes(StandardCharsets.UTF_8));
    return new SecretKeySpec(k, "AES");
  }
}
