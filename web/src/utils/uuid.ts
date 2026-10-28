/**
 * 生成 UUID v4。
 *
 * 优先用 crypto.randomUUID（仅安全上下文可用：https / localhost）。
 * HTTP + IP 部署（如 http://14.103.23.160/...）下 crypto.randomUUID 为 undefined，
 * 必须降级到 crypto.getRandomValues 手拼 v4，否则抛 TypeError。
 */
export function newId(): string {
  const c = typeof globalThis !== 'undefined' ? (globalThis.crypto as Crypto | undefined) : undefined;
  if (c && typeof c.randomUUID === 'function') {
    return c.randomUUID();
  }
  // 降级：用手拼 UUID v4
  const bytes = new Uint8Array(16);
  if (c && typeof c.getRandomValues === 'function') {
    c.getRandomValues(bytes);
  } else {
    // 极端兜底：无 crypto（老浏览器）用 Math.random
    for (let i = 0; i < 16; i++) bytes[i] = Math.floor(Math.random() * 256);
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40; // version 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // variant 10
  const hex: string[] = [];
  for (let i = 0; i < 16; i++) hex.push(bytes[i].toString(16).padStart(2, '0'));
  return (
    hex.slice(0, 4).join('') + '-' +
    hex.slice(4, 6).join('') + '-' +
    hex.slice(6, 8).join('') + '-' +
    hex.slice(8, 10).join('') + '-' +
    hex.slice(10, 16).join('')
  );
}
