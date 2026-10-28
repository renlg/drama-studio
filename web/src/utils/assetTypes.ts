/** 图片类资产类型（与后端 NodeAssetService.IMAGE 保持一致） */
export const IMAGE_TYPES = [
  'STORYBOARD_IMAGE',
  'CHARACTER_REFERENCE_IMAGE',
  'SCENE_REFERENCE_IMAGE',
  'PROP_REFERENCE_IMAGE',
] as const;

/** 视频类资产类型 */
export const VIDEO_TYPES = ['SHOT_VIDEO'] as const;

export const isImageAsset = (t: string) => (IMAGE_TYPES as readonly string[]).includes(t);
export const isVideoAsset = (t: string) => (VIDEO_TYPES as readonly string[]).includes(t);
export const isTextAsset = (t: string) => !isImageAsset(t) && !isVideoAsset(t);
