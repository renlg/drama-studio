import {api} from './client';

export type ModelCategory = 'TEXT' | 'IMAGE' | 'VIDEO' | 'AUDIO';

export interface ProviderModel {
  id: string;
  providerId: string;
  category: ModelCategory;
  modelId: string;
  displayName: string;
  isDefault: boolean;
  enabled: boolean;
  sortOrder: number;
  extraJson?: string | null;
}

export interface ModelProvider {
  id: string;
  name: string;
  baseUrl: string;
  isDefault: boolean;
  enabled: boolean;
  sortOrder: number;
  hasKey: boolean;
  keyMask: string;
  models: Record<ModelCategory, ProviderModel[]>;
  modelCount: number;
}

export const listProviders = (enabledOnly = false) =>
  api.get<{providers: ModelProvider[]}>('/model-providers', {params: {enabledOnly}}).then(r => r.data.providers);

export const listModels = (providerId: string, category: ModelCategory, enabledOnly = false) =>
  api.get<{models: ProviderModel[]}>(`/model-providers/${providerId}/models`, {params: {category, enabledOnly}})
     .then(r => r.data.models);

export const createProvider = (body: {name: string; baseUrl: string; apiKey?: string; makeDefault?: boolean; enabled?: boolean; sortOrder?: number}) =>
  api.post<ModelProvider>('/model-providers', body).then(r => r.data);

export const updateProvider = (id: string, body: {name?: string; baseUrl?: string; apiKey?: string; enabled?: boolean; sortOrder?: number}) =>
  api.put<ModelProvider>(`/model-providers/${id}`, body).then(r => r.data);

export const deleteProvider = (id: string) => api.delete(`/model-providers/${id}`).then(r => r.data);

export const setDefaultProvider = (id: string) => api.post(`/model-providers/${id}/default`).then(r => r.data);

export const addModel = (providerId: string, body: {category: ModelCategory; modelId: string; displayName?: string; makeDefault?: boolean; enabled?: boolean; sortOrder?: number; extraJson?: string}) =>
  api.post<ProviderModel>(`/model-providers/${providerId}/models`, body).then(r => r.data);

export const updateModel = (providerId: string, modelRowId: string, body: {modelId?: string; displayName?: string; enabled?: boolean; sortOrder?: number; extraJson?: string}) =>
  api.put<ProviderModel>(`/model-providers/${providerId}/models/${modelRowId}`, body).then(r => r.data);

export const deleteModel = (providerId: string, modelRowId: string) =>
  api.delete(`/model-providers/${providerId}/models/${modelRowId}`).then(r => r.data);

export const setDefaultModel = (providerId: string, modelRowId: string) =>
  api.post(`/model-providers/${providerId}/models/${modelRowId}/default`).then(r => r.data);

export const testProvider = (id: string) =>
  api.post<{ok: boolean; baseUrl: string; hasKey: boolean; message: string}>(`/model-providers/${id}/test`).then(r => r.data);

/** 节点类型 -> 模型分类 */
export function categoryOfNodeType(type: string): ModelCategory {
  if (type === 'VIDEO') return 'VIDEO';
  if (type === 'AUDIO') return 'AUDIO';
  if (type === 'TEXT') return 'TEXT';
  return 'IMAGE'; // IMAGE / CHARACTER / SCENE
}

/** 取某分类下生效的供应商+模型（优先节点已配，其次默认供应商默认模型） */
export function resolveSelection(
  providers: ModelProvider[],
  category: ModelCategory,
  wantProviderId?: string,
  wantModelId?: string,
): {provider: ModelProvider | undefined; model: ProviderModel | undefined} {
  const usable = providers.filter(p => p.enabled && (p.models[category] ?? []).some(m => m.enabled));
  const provider =
    usable.find(p => p.id === wantProviderId) ||
    usable.find(p => p.isDefault) ||
    usable[0];
  if (!provider) return {provider: undefined, model: undefined};
  const models = (provider.models[category] ?? []).filter(m => m.enabled);
  const model = models.find(m => m.modelId === wantModelId) || models.find(m => m.isDefault) || models[0];
  return {provider, model};
}
