import axios from 'axios';
import { BASE, clearToken, getToken, goLogin } from '../auth';

export const api = axios.create({ baseURL: BASE + '/api/v1' });

api.interceptors.request.use((c) => {
  const token = getToken();
  if (token) c.headers.Authorization = `Bearer ${token}`;
  return c;
});

api.interceptors.response.use(
  (r) => r,
  (r) => {
    if (r.response?.status === 401) {
      clearToken();
      // 回调页正在换 token，不能在这里打断
      if (!location.pathname.includes('login-callback')) goLogin();
    }
    return Promise.reject(r);
  },
);

export const unwrap = <T>(p: Promise<{ data: T }>) => p.then((r) => r.data);
