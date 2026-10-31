import { useStorage } from '@vueuse/core';
import Cookies from 'js-cookie';

const suffix = APP_TARGET.toUpperCase();
const tokenKey = '__TOKEN__' + suffix;
const tokenState = useStorage(tokenKey, '');
const refreshTokenState = useStorage('__REFRESH_TOKEN__' + suffix, '');
const tokenExpAtState = useStorage('__TOKEN_EXP__' + suffix, 0);
const sessionTokenKey = '__SESSION_TOKEN__' + suffix;
const sessionLoginKey = '__SESSION_LOGIN__' + suffix;
const sessionRefreshKey = '__SESSION_REFRESH_TOKEN__' + suffix;
const sessionExpKey = '__SESSION_TOKEN_EXP__' + suffix;

/** 独立终端的登录入口；保留旧版本已签发的标签页 token。 */
export function getSessionLoginPath(): string {
  return sessionStorage.getItem(sessionLoginKey) || (sessionStorage.getItem(sessionTokenKey) ? '/login' : '');
}

/** 将当前标签页隔离为独立终端，不修改其他标签页的管理端凭据。 */
export function beginSessionAuthentication(loginPath = '/login') {
  if (!/^\/(?!\/)/.test(loginPath)) throw new Error('无效的终端登录入口');
  sessionStorage.setItem(sessionLoginKey, loginPath);
}

/** 管理端按构建开关选择认证；已明确进入的独立终端保留自身 Bearer 会话。 */
export function isOAuth2Authentication(): boolean {
  return ENABLE_OAUTH2 === true && !getSessionLoginPath();
}

/** 保存访问令牌，独立终端的登录和续期始终留在当前标签页。 */
export function setToken(token: string) {
  if (getSessionLoginPath()) {
    sessionStorage.setItem(sessionTokenKey, token);
    return;
  }
  localStorage.setItem(tokenKey, token);
  tokenState.value = token;
}

/** 刷卡等独立登录不继承管理端 refresh token，也不清除管理端登录态。 */
export function setSessionToken(token: string, loginPath = '/login') {
  beginSessionAuthentication(loginPath);
  sessionStorage.setItem(sessionTokenKey, token);
  sessionStorage.removeItem(sessionRefreshKey);
  sessionStorage.removeItem(sessionExpKey);
}

/** 将本地登录复制到独立终端；已有终端会话直接沿用，OAuth2 Session 不转换为 JWT。 */
export function moveTokenToSession(loginPath = '/login'): string {
  if (getSessionLoginPath()) return getToken();
  if (isOAuth2Authentication()) return '';
  const token = getToken(), refresh = getRefreshToken(), exp = getTokenExpAt();
  beginSessionAuthentication(loginPath);
  setToken(token); setRefreshToken(refresh); setTokenExpAt(exp);
  return token;
}

/** OAuth2 管理端不读取历史本地令牌；终端只读取自己的标签页凭据。 */
export function getToken(): string {
  if (getSessionLoginPath()) return sessionStorage.getItem(sessionTokenKey) || '';
  return isOAuth2Authentication() ? '' : tokenState.value;
}

export function setRefreshToken(token: string) {
  if (getSessionLoginPath()) sessionStorage.setItem(sessionRefreshKey, token || '');
  else refreshTokenState.value = token || '';
}

export function getRefreshToken(): string {
  if (getSessionLoginPath()) return sessionStorage.getItem(sessionRefreshKey) || '';
  return isOAuth2Authentication() ? '' : refreshTokenState.value;
}

export function setTokenExpAt(ms: number) {
  if (getSessionLoginPath()) sessionStorage.setItem(sessionExpKey, String(ms || 0));
  else tokenExpAtState.value = ms || 0;
}

export function getTokenExpAt(): number {
  if (getSessionLoginPath()) return Number(sessionStorage.getItem(sessionExpKey)) || 0;
  return isOAuth2Authentication() ? 0 : tokenExpAtState.value;
}

/** 仅清理当前认证来源；终端退出不退出管理端，OAuth2 退出由服务端处理 Session。 */
export function removeToken() {
  if (getSessionLoginPath()) {
    [sessionTokenKey, sessionRefreshKey, sessionExpKey].forEach(key => sessionStorage.removeItem(key));
    return;
  }
  if (isOAuth2Authentication()) return;
  tokenState.value = '';
  refreshTokenState.value = '';
  tokenExpAtState.value = 0;
  Cookies.remove('Authorization');
}

/** 请求只选择已启用的认证来源；该标记不会替代服务端的凭据和权限校验。 */
export function getAuthenticationHeaders(token: string | null = getToken(), header = 'Authorization'): Record<string, string> {
  if (isOAuth2Authentication()) return { 'X-Web-Auth': 'oauth2' };
  return {
    'X-Web-Auth': 'jwt2',
    ...(token ? { [header]: /^Bearer /i.test(token) ? token : 'Bearer ' + token } : {}),
  };
}

/** 只有普通 JWT 标签页同步持久登录；SSO 和独立终端不受其他标签页 token 变化影响。 */
export function onSharedTokenChange(changed: (token: string) => void): () => void {
  const check = () => {
    const token = getCurrentSharedToken();
    if (token !== undefined) changed(token);
  };
  const storageChanged = (event: StorageEvent) => {
    if (event.storageArea === localStorage && event.key === tokenKey) check();
  };
  window.addEventListener('storage', storageChanged);
  window.addEventListener('focus', check);
  return () => {
    window.removeEventListener('storage', storageChanged);
    window.removeEventListener('focus', check);
  };
}

export function getCurrentSharedToken(): string | undefined {
  if (getSessionLoginPath() || isOAuth2Authentication()) return undefined;
  return localStorage.getItem(tokenKey) || '';
}
