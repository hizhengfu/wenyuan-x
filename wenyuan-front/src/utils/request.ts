import axios, { AxiosRequestConfig, AxiosResponse } from 'axios';
// @ts-ignore
import Qs from 'qs';
// import Notify from '../components/basic/notify'
import { globalRouter } from '@/router';
import { notification } from '@/utils/message';
import { useAuthStore, useAppStore } from '@/store';
import { getRefreshToken, getTokenExpAt, setRefreshToken, setToken, setTokenExpAt } from '@/utils/auth';

declare module 'axios' {
  interface AxiosRequestConfig {
    /** 调用方自行展示失败状态；401 仍按原流程刷新或退出登录。 */
    silentError?: boolean;
  }
}

/**
 * 通用数据接口
 * */
export interface HttpResponse<T = unknown> {
  status: number;
  message: string;
  type: string;
  timestamp: string;
  data: T;
}

import { getAuthenticationHeaders, isOAuth2Authentication } from './auth';

// 创建axios实例
const service = axios.create({
  withCredentials: true,
  // baseURL: APP_BASE_URL+process.env.APP_API_PATH, // api 的 API_URL
  baseURL: APP_BASE_URL, // api 的 API_URL
  /*paramsSerializer: function (params) {
      return Qs.stringify(params, {
          arrayFormat: 'brackets',
          serializeDate: function (d) {
              return d.toISOString()
          }
      })
  },*/
  paramsSerializer: { indexes: null },
  timeout: 60000, // 请求超时时间
});

type IdentityRequest = AxiosRequestConfig & { authIdentityRevision?: number };
function requireCurrentIdentity(config?: IdentityRequest) {
  const auth = useAuthStore();
  auth.reconcileSharedIdentity();
  if (auth.identitySyncing || (config?.authIdentityRevision !== undefined && config.authIdentityRevision !== auth.identityRevision))
    throw new axios.CanceledError('登录身份已变化，请刷新页面');
}

/** 仅 jwt2 开启刷新：无 refreshToken（旧 jwt 模块）直接跳过；登录/刷新/登出接口自身不触发刷新 */
function canRefresh(url?: string): boolean {
  if (isOAuth2Authentication() || !getRefreshToken()) return false;
  return !/auth\/(refresh|login|logout)/.test(url || '');
}

let refreshing: Promise<string> | null = null;

function doRefresh(): Promise<string> {
  const auth = useAuthStore();
  const revision = auth.identityRevision;
  const previousToken = auth.token;
  const rt = getRefreshToken();
  if (!rt) return Promise.reject(new Error('no_refresh'));
  // 用裸 axios 调用，避免走本实例拦截器导致递归
  return axios
    .post(
      `${APP_BASE_URL}${APP_API_PATH}/auth/refresh`,
      { refreshToken: rt },
      { withCredentials: true, headers: { 'Content-Type': 'application/json', 'X-Web-Auth': 'jwt2' } },
    )
    .then((resp) => {
      auth.reconcileSharedIdentity();
      if (auth.identitySyncing || auth.identityRevision !== revision || auth.token !== previousToken) throw new axios.CanceledError('登录身份已变化');
      const p = resp.data?.data ?? resp.data;
      if (!p?.accessToken) throw new Error('invalid_refresh_response');
      auth.token = p.accessToken;
      setToken(p.accessToken);
      setRefreshToken(p.refreshToken || '');
      setTokenExpAt(p.expiresIn ? Date.now() + p.expiresIn * 1000 : 0);
      return p.accessToken as string;
    });
}

function refreshOnce(): Promise<string> {
  if (!refreshing) {
    refreshing = doRefresh().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

// request拦截器
service.interceptors.request.use(
  async (config) => {
    const authStore = useAuthStore();
    (config as IdentityRequest).authIdentityRevision ??= authStore.identityRevision;
    requireCurrentIdentity(config);
    const refreshLogout = /auth\/logout(?:[/?]|$)/.test(config.url || '') && !!getRefreshToken();
    let token = refreshLogout ? null : authStore.getToken;
    // 浏览器 Cookie 无法按请求撤回；让服务端只接受本管理端选定的认证来源。
    if (isOAuth2Authentication()) config.withCredentials = true;
    config.headers.delete(authStore.authorizationName);
    Object.entries(getAuthenticationHeaders(token, authStore.authorizationName)).forEach(([name, value]) => config.headers.set(name, value));
    // !config.headers && (config.headers = {})
    if (token) {
      // 主动刷新：接近过期且 jwt2 有 refreshToken 时先刷
      if (canRefresh(config.url)) {
        const expAt = getTokenExpAt();
        if (expAt && expAt - Date.now() < 60_000) {
          try {
            await refreshOnce();
            token = authStore.getToken;
          } catch {
            // 刷新失败留给 401 兜底
          }
        }
      }
      requireCurrentIdentity(config);
      config.headers[authStore.authorizationName] = token;
    } else if (!isOAuth2Authentication()) {
      //处理特殊的自带token的情况
      let hash = location.hash;
      if (location.hash.indexOf('?') !== -1) {
        hash = location.hash.split('?')[1];
      }
      let query = Qs.parse(hash);
      if (query['token']) {
        config.headers.Token = decodeURIComponent(query['token'] as string);
      }
    }

    if (!config.headers['Content-Type']) {
      config.headers['Content-Type'] = 'application/json';
    }

    //判断是否是http起始的路径
    if (config.url && !/https?:\/\//i.test(config.url)) {
      const url = config.url.replace(/^\/?/, '/');
      // rawUrl=true：调用方明确表示路径已完整，不需要拼接 APP_API_PATH
      // 例如 nginx 直接代理的非业务接口（/stream/api/whep 等）
      //@ts-ignore
      if (!config.rawUrl && url.indexOf(APP_API_FRONT_PATH) === -1) {
        config.url = APP_API_PATH + url;
      }

      //是否是代理请求,或者接口强制不走网关
      //@ts-ignore
      if (APP_API_PROXY === true && !config.disableGateway) {
        config.params = { ...config.params, ...{ proxyPath: config.url, proxyMethod: config.method } };
        config.method = 'post';
        config.url = APP_API_PATH + '/gateway/post';
        //@ts-ignore
      } else if (config.enableGateway == true) {
        config.params = { ...config.params, ...{ proxyPath: config.url } };
        config.url = APP_API_PATH + '/gateway';
      }
    }
    requireCurrentIdentity(config);
    return config;
  },
  (error) => {
    // Do something with request error
    // console.log(error) // for debug
    return Promise.reject(error);
  },
);

// response 拦截器

service.interceptors.response.use(
  // @ts-ignore
  (response: AxiosResponse<HttpResponse>) => {
    requireCurrentIdentity(response.config);
    //网络正常后如果在网络异常界面应该能返回到正常界面
    if (location.hash.indexOf('/error/network') > -1) {
      let redirect = globalRouter.currentRoute.value.query.redirect;
      if (redirect) {
        globalRouter.replace(redirect as string);
      }
    }
    if ((response.config as AxiosRequestConfig & { returnResponse?: boolean }).returnResponse) return response;
    const message = response.data.message;

    if (message) {
      notification({
        type: response.data.type || 'success',
        title: '请注意',
        content: message,
      });
    }

    return response.data;
  },
  async (error) => {
    // 组件切换或关闭的主动取消无需提示网络错误。
    if (axios.isCancel(error)) return Promise.reject(error);
    requireCurrentIdentity(error.config);
    if (error.config?.silentError && error.response?.status !== 401) return Promise.reject(error);
    let code = 0;
    let err = error.response?.data?.message || error.message;

    const appStore = useAppStore();

    // console.log(JSON.stringify(error))
    const authStore = useAuthStore();
    if (error.response) {
      code = error.response.status;

      if (code === 401) {
        const cfg = error.config || {};
        const isLoginOrLogoutRequest = /auth\/(login|logout)(?:[/?]|$)/.test(cfg.url || '');
        // jwt2：有 refreshToken 且非刷新/登录路径，则尝试刷新一次后重放
        if (!cfg._retry && canRefresh(cfg.url)) {
          cfg._retry = true;
          try {
            const t = await refreshOnce();
            requireCurrentIdentity(cfg);
            cfg.headers = cfg.headers || {};
            cfg.headers[authStore.authorizationName] = 'Bearer ' + t;
            return service(cfg);
          } catch {
            // 刷新失败 → 继续走登出
          }
        }

        requireCurrentIdentity(cfg);
        if (isLoginOrLogoutRequest) {
          notification({
            type: 'warning',
            title: '请注意',
            content: err as string,
          });
        } else if (authStore.handleUnauthorized()) {
          notification({
            type: 'warning',
            title: '应用未经授权，登录后才能使用',
            content: '请重新登录',
          });
        }
        // globalRouter.push({path: '/login'})
      } /* else if (code === 403) {
        globalRouter.push({path: '/error/403'})
      }*/ else if (code === 500) {
        // globalRouter.push({ path: '/error/500' });
        notification({
          type: 'warning',
          title: '请注意',
          content: err,
        });
      } else if (code === 502) {
        // 502 由调用方或页面自行兜底，避免全局弹窗干扰使用。
      } else {
        notification({
          type: 'warning',
          title: '请注意',
          content: err as string,
        });
      }
    } else {
      if (appStore.silent !== true) {
        //网络异常的时候跳转到网络异常界面，同时记录当前链接
        if (err === 'Network Error') {
          //当前界面非网络异常界面，增加重定向url
          if (location.hash.indexOf('/error/network') == -1) {
            let hash = location.hash.replace(/^#/, '');
            globalRouter.push({ path: '/error/network', query: { redirect: hash || '/' } });
          }
          err = '接口不能访问，请检查网络';
        }
        notification({
          type: 'warning',
          title: '请注意',
          content: err,
        });
      }
    }
    return Promise.reject(error);
  },
);
export default service;
