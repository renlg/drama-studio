/** 认证相关工具：子目录部署的所有跳转都必须带 BASE 前缀，否则 404 */
export const BASE = '/drama-studio';

const TOKEN_KEY = 'token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

export function isLoggedIn(): boolean {
  return !!getToken();
}

/** 跳转到后端 OAuth 入口（会 302 到 ai-connect 授权页） */
export function goLogin(): void {
  location.href = BASE + '/auth/login';
}
