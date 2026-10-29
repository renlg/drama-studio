/** 上传文件类型：accept 与 assetType 统一在此推导，避免各组件各写一套。 */

const IMAGE_TYPES = ['IMAGE', 'CHARACTER', 'SCENE'];

/** 节点的文件选择器 accept。
 * 关键：VIDEO 节点必须同时接受图片——「图生视频」的首帧就是图片，
 * 之前写死 video/* 导致文件选择器里图片全是灰的、点不动。 */
export function uploadAccept(nodeType: string, tool?: string): string {
  if (nodeType === 'AUDIO') return 'audio/*';
  if (nodeType === 'VIDEO') {
    if (tool === '图生视频') return 'image/*';
    return 'video/*,image/*';
  }
  if (IMAGE_TYPES.includes(nodeType)) return 'image/*';
  return '';
}

export function canUploadNode(nodeType: string): boolean {
  return ['IMAGE', 'CHARACTER', 'SCENE', 'VIDEO', 'AUDIO'].includes(nodeType);
}

/** 依据文件真实 MIME 决定资产类型，不再按节点类型强行指定。
 * 之前 VIDEO 节点传图片会带 assetType=VIDEO，后端优先信前端，
 * 结果图片被存成 VIDEO，节点内预览按图片筛永远取不到。 */
export function assetTypeOfFile(file: File): string {
  const t = file.type || '';
  if (t.startsWith('image/')) return 'IMAGE';
  if (t.startsWith('video/')) return 'VIDEO';
  if (t.startsWith('audio/')) return 'AUDIO';
  // 无 MIME 时按扩展名兜底
  const ext = (file.name.split('.').pop() || '').toLowerCase();
  if (['png', 'jpg', 'jpeg', 'webp', 'gif', 'bmp'].includes(ext)) return 'IMAGE';
  if (['mp4', 'mov', 'webm', 'mkv', 'avi'].includes(ext)) return 'VIDEO';
  if (['mp3', 'wav', 'm4a', 'aac', 'ogg'].includes(ext)) return 'AUDIO';
  return 'IMAGE';
}

/** 提交前校验：文件类型是否在该节点允许范围内。 */
export function fileAllowed(nodeType: string, tool: string | undefined, file: File): boolean {
  const accept = uploadAccept(nodeType, tool);
  if (!accept) return false;
  const groups = accept.split(',');
  const t = file.type || '';
  if (!t) return true; // 拿不到 MIME 时交给后端判定
  return groups.some(g => (g.endsWith('/*') ? t.startsWith(g.slice(0, -1)) : t === g));
}
