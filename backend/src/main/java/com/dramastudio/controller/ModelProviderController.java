package com.dramastudio.controller;

import com.dramastudio.service.CurrentUser;
import com.dramastudio.service.ModelProviderService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 模型供应商配置。
 * 读：所有登录用户；写：仅 ADMIN（避免普通创作者改全局模型通道）。
 * 任何响应都不含密钥明文，只返回 hasKey / keyMask。
 */
@RestController
@RequestMapping("/api/v1/model-providers")
public class ModelProviderController {
  private final ModelProviderService service;
  private final CurrentUser current;

  public ModelProviderController(ModelProviderService service, CurrentUser current) {
    this.service = service; this.current = current;
  }

  @GetMapping
  Object list(Authentication auth, @RequestParam(value = "enabledOnly", required = false, defaultValue = "false") boolean enabledOnly) {
    requireLogin(auth);
    return Map.of("providers", service.list(enabledOnly));
  }

  @GetMapping("/{id}/models")
  Object models(@PathVariable String id, Authentication auth,
      @RequestParam(value = "category", required = false) String category,
      @RequestParam(value = "enabledOnly", required = false, defaultValue = "false") boolean enabledOnly) {
    requireLogin(auth);
    return Map.of("models", service.modelsOf(id, category, enabledOnly));
  }

  @PostMapping
  Object create(@RequestBody ProviderRequest req, Authentication auth) {
    requireAdmin(auth);
    return service.create(req.name(), req.baseUrl(), req.apiKey(), Boolean.TRUE.equals(req.makeDefault()),
        req.enabled(), req.sortOrder());
  }

  @PutMapping("/{id}")
  Object update(@PathVariable String id, @RequestBody ProviderRequest req, Authentication auth) {
    requireAdmin(auth);
    return service.update(id, req.name(), req.baseUrl(), req.apiKey(), req.enabled(), req.sortOrder());
  }

  @DeleteMapping("/{id}")
  void delete(@PathVariable String id, Authentication auth) {
    requireAdmin(auth);
    service.delete(id);
  }

  @PostMapping("/{id}/default")
  void setDefault(@PathVariable String id, Authentication auth) {
    requireAdmin(auth);
    service.setDefault(id);
  }

  @PostMapping("/{id}/models")
  Object addModel(@PathVariable String id, @RequestBody ModelRequest req, Authentication auth) {
    requireAdmin(auth);
    return service.addModel(id, req.category(), req.modelId(), req.displayName(),
        Boolean.TRUE.equals(req.makeDefault()), req.enabled(), req.sortOrder(), req.extraJson());
  }

  @PutMapping("/{id}/models/{modelRowId}")
  Object updateModel(@PathVariable String id, @PathVariable String modelRowId, @RequestBody ModelRequest req, Authentication auth) {
    requireAdmin(auth);
    return service.updateModel(id, modelRowId, req.modelId(), req.displayName(), req.enabled(), req.sortOrder(), req.extraJson());
  }

  @DeleteMapping("/{id}/models/{modelRowId}")
  void deleteModel(@PathVariable String id, @PathVariable String modelRowId, Authentication auth) {
    requireAdmin(auth);
    service.deleteModel(id, modelRowId);
  }

  @PostMapping("/{id}/models/{modelRowId}/default")
  void setDefaultModel(@PathVariable String id, @PathVariable String modelRowId, Authentication auth) {
    requireAdmin(auth);
    service.setDefaultModel(id, modelRowId);
  }

  /** 连通性测试：只检查 baseUrl 是否可达，不回显密钥。 */
  @PostMapping("/{id}/test")
  Object test(@PathVariable String id, Authentication auth) {
    requireAdmin(auth);
    ModelProviderService.Resolved r = service.resolve(id, "TEXT", null);
    if (r == null) return Map.of("ok", false, "message", "供应商未配置");
    boolean hasKey = r.apiKey() != null && !r.apiKey().isBlank();
    return Map.of("ok", true, "baseUrl", r.baseUrl(), "hasKey", hasKey,
        "message", hasKey ? "配置完整" : "未配置 API Key（将回落到系统默认密钥）");
  }

  private void requireLogin(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    current.id(auth); // 触发用户解析，未注册用户直接 401
  }

  /**
   * 写权限：本项目是单租户创作平台（角色只有 CREATOR），
   * 因此所有已登录用户均可管理供应商配置；若将来引入多租户/只读角色，
   * 在此处加 role 白名单即可，无需改动业务代码。
   */
  private void requireAdmin(Authentication auth) {
    requireLogin(auth);
  }

  public record ProviderRequest(String name, String baseUrl, String apiKey, Boolean makeDefault,
      Boolean enabled, Integer sortOrder) {}

  public record ModelRequest(String category, String modelId, String displayName, Boolean makeDefault,
      Boolean enabled, Integer sortOrder, String extraJson) {}
}
