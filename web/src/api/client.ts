import axios from 'axios';
export const api=axios.create({baseURL:'/drama-studio/api/v1'});api.interceptors.request.use(c=>{const token=localStorage.getItem('token');if(token)c.headers.Authorization=`Bearer ${token}`;return c});api.interceptors.response.use(r=>r,r=>{if(r.response?.status===401&&!location.pathname.includes('login-callback'))location.href='/drama-studio/auth/login';return Promise.reject(r)});
export const unwrap=<T>(p:Promise<{data:T}>)=>p.then(r=>r.data);
