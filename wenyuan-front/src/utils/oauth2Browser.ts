let navigating = false;

/** 只接受本站 SPA 路由，阻止 redirect 查询参数将认证返回地址指向站外。 */
function returnUrl(redirect: string): string {
  const page = new URL(window.location.href);
  page.hash = /^\/(?!\/)/.test(redirect) && !redirect.startsWith('/login') ? redirect : '/';
  return page.href;
}

/** OAuth2 使用整页导航，浏览器回调后由服务端恢复已校验的前端地址。 */
export function startOAuth2Login(redirect = '/'): boolean {
  if (navigating) return false;
  const url = new URL(`${APP_BASE_URL.replace(/\/$/, '')}/oauth2/login`, window.location.href);
  url.searchParams.set('returnTo', returnUrl(redirect));
  navigating = true;
  window.location.assign(url.href);
  return true;
}

/** 退出到服务端 OIDC 端点，保留 ID Token 提示和认证中心退出流程。 */
export function startOAuth2Logout(): boolean {
  if (navigating) return false;
  navigating = true;
  window.location.assign(new URL(`${APP_BASE_URL.replace(/\/$/, '')}/logout`, window.location.href).href);
  return true;
}
