import {create} from 'zustand';
import {api} from '../api/client';

export interface CatalogParam {
  type: string;
  defaultValue: unknown;
  editable: boolean;
  source: string;
}
export interface CatalogPort {
  name: string;
  type: string;
  required: boolean;
  multiple: boolean;
}
export interface CatalogDef {
  type: string;
  label: string;
  capability: string;
  inputs: CatalogPort[];
  outputs: CatalogPort[];
  params: Record<string, CatalogParam>;
}

interface CatalogState {
  defs: Record<string, CatalogDef>;
  loaded: boolean;
  load: () => Promise<void>;
}

/** 节点定义（参数类型 + 默认值）全局缓存：只请求一次，供拖拽建节点和参数面板复用 */
export const useCatalog = create<CatalogState>((set, get) => ({
  defs: {},
  loaded: false,
  load: async () => {
    if (get().loaded) return;
    const {data} = await api.get('/templates/node-catalog');
    set({defs: data as Record<string, CatalogDef>, loaded: true});
  },
}));

/** 按目录默认值生成一份完整配置，用于新建节点 */
export function defaultConfig(def?: CatalogDef): Record<string, unknown> {
  if (!def) return {};
  const out: Record<string, unknown> = {};
  for (const [k, p] of Object.entries(def.params)) {
    out[k] = clone(p.defaultValue);
  }
  return out;
}

function clone(v: unknown): unknown {
  if (Array.isArray(v)) return [...v];
  if (v && typeof v === 'object') return {...(v as object)};
  return v;
}

/** 参数中文名：目录里只有英文字段名，这里做一层可读映射 */
export const PARAM_LABELS: Record<string, string> = {
  title: '标题',
  story_environment: '故事环境',
  genre: '题材',
  drama_type: '剧类型',
  art_style: '画风',
  panel_style: '画幅',
  voice_mode: '声音模式',
  episode_count: '集数',
  episode_duration_sec: '单集时长(秒)',
  target_audience: '目标受众',
  core_payoff: '核心爽点',
  emotional_tone: '情绪基调',
  idea: '故事创意',
  structure: '结构',
  payoff_density: '爽点密度',
  hook_style: '钩子风格',
  character_limit: '角色数量上限',
  extra_constraints: '额外约束',
  character_scope: '角色范围',
  character_names: '角色名单',
  reference_background: '参考图背景',
  reference_views: '参考视角',
  prop_policy: '道具策略',
  forbidden_changes: '禁止变更项',
  episode_number: '集数编号',
  auto_refresh: '自动刷新',
  episode_title: '分集标题',
  episode_scope: '分集范围',
  episode_list: '指定集数',
  split_strategy: '拆分策略',
  target_duration_sec: '目标时长(秒)',
  dialogue_ratio: '对白占比',
  target_payoff: '本集爽点',
  ending_hook: '结尾钩子',
  episode_id: '分集 ID',
  target_shot_duration_sec: '单镜头时长',
  shot_count_hint: '镜头数',
  camera_language: '镜头语言',
  include_dialogue_caption: '包含对白字幕',
  include_comic_effects: '包含漫画特效',
  continuity_constraints: '连贯性约束',
  character_id: '角色 ID',
  lock_prompt: '锁定提示词',
  background: '背景',
  subject_rule: '主体规则',
  views: '视角',
  negative_prompt: '反向提示词',
  width: '宽',
  height: '高',
  seed: '随机种子',
  output_count: '生成数量',
  shot_id: '镜头 ID',
  character_lock: '角色锁定',
  reference_images: '参考图',
  scene_description: '场景描述',
  composition: '构图',
  emotion: '情绪',
  action: '动作',
  quality_prompt: '质量提示词',
  denoise: '重绘幅度',
  source_image: '源图',
  reference_image: '参考图',
  duration_sec: '时长(秒)',
  start_frame_description: '起始画面',
  motion_description: '动作描述',
  camera_movement: '运镜',
  expression_change: '表情变化',
  environment_motion: '环境动态',
  pace: '节奏',
  audio_hint: '音频提示',
  consistency_constraints: '一致性约束',
  resolution: '分辨率',
  mode: '视频模式',
  provider: '供应商',
  model: '模型',
  timeout_sec: '超时(秒)',
  retry_count: '重试次数',
  temperature: '温度',
  max_tokens: '最大 Token',
  project_state: '项目状态',
  outline: '大纲',
  characters: '角色',
  shots: '分镜',
  character: '角色',
  episode_script: '分集剧本',
  shot: '镜头',
  system_prompt: '系统提示词',
  user_prompt: '用户提示词',
  variables: '变量',
  output_format: '输出格式',
  json_schema: 'JSON Schema',
  asset_category: '素材分类',
  files: '文件',
  required_status: '要求状态',
  description: '描述',
};

export function paramLabel(key: string): string {
  return PARAM_LABELS[key] ?? key;
}
