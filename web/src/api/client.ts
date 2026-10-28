import axios from 'axios';
import { BASE, clearToken, getToken } from '../auth';

export const api = axios.create({ baseURL: BASE + '/api/v1' });

api.interceptors.request.use((c) => {
  const token = getToken();
  if (token) c.headers.Authorization = `Bearer ${token}`;
  return c;
});

api.interceptors.response.use(
  (r) => r,
  (r) => {
    const st = r.response?.status;
    if (st === 401) {
      // 未认证/token 过期 → 清 token 整页回登录页
      clearToken();
      if (!location.pathname.includes('login-callback')) location.replace(BASE + '/login');
    } else if (st === 403) {
      // 后端对无 token 也返回 403（兼容旧行为），同样处理
      clearToken();
      if (!location.pathname.includes('login-callback')) location.replace(BASE + '/login');
    }
    return Promise.reject(r);
  },
);

export const unwrap = <T>(p: Promise<{ data: T }>) => p.then((r) => r.data);
