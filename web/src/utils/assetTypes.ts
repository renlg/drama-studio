/** 资产大类（与后端 WorkflowExecutor.outputType 保持一致）
 * 6 类节点重构后，后端产出的 assetType 只有这几种：
 *   TEXT / IMAGE / VIDEO / AUDIO / CHARACTER / SCENE / GENERIC_OUTPUT
 * 旧的细分类型（STORYBOARD_IMAGE 等）已废弃，保留在 LEGACY_* 里做兼容。
 */
export const IMAGE_TYPES = ['IMAGE', 'CHARACTER', 'SCENE'] as const;

/** 视频类资产类型 */
export const VIDEO_TYPES = ['VIDEO'] as const;

/** 音频类资产类型 */
export const AUDIO_TYPES = ['AUDIO'] as const;

/** 旧版细分类型（兼容历史数据，不要再用于新产出判断） */
export const LEGACY_IMAGE_TYPES = [
  'STORYBOARD_IMAGE',
  'CHARACTER_REFERENCE_IMAGE',
  'SCENE_REFERENCE_IMAGE',
  'PROP_REFERENCE_IMAGE',
] as const;
export const LEGACY_VIDEO_TYPES = ['SHOT_VIDEO'] as const;

export const isImageAsset = (t: string) =>
  (IMAGE_TYPES as readonly string[]).includes(t) ||
  (LEGACY_IMAGE_TYPES as readonly string[]).includes(t);

export const isVideoAsset = (t: string) =>
  (VIDEO_TYPES as readonly string[]).includes(t) ||
  (LEGACY_VIDEO_TYPES as readonly string[]).includes(t);

export const isAudioAsset = (t: string) =>
  (AUDIO_TYPES as readonly string[]).includes(t);

export const isTextAsset = (t: string) =>
  !isImageAsset(t) && !isVideoAsset(t) && !isAudioAsset(t);
