const LOOPBACK_ALIASES = new Set(['127.0.0.1', '::1', '[::1]']);

/**
 * 工作站浏览器凭据是 Host-only HttpOnly Cookie。开发环境必须把 127.0.0.1 / ::1
 * 收敛到 localhost，避免注册后从另一个回环别名打开快捷方式而丢失绑定。
 */
export function canonicalRfidWorkstationUrl(href: string, baseHref: string): string {
  const url = new URL(href, baseHref);
  if (LOOPBACK_ALIASES.has(url.hostname)) url.hostname = 'localhost';
  return url.toString();
}

export function ensureCanonicalRfidWorkstationHost(
  location: Pick<Location, 'href' | 'replace'> = window.location,
): boolean {
  const canonicalUrl = canonicalRfidWorkstationUrl(location.href, location.href);
  if (canonicalUrl === location.href) return false;
  location.replace(canonicalUrl);
  return true;
}
