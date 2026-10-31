export function requirePackagePath(path: string) {
  if (
    !path.trim() ||
    path.startsWith('/') ||
    /[\\:\x00-\x1f\x7f]/.test(path) ||
    path.split('/').some((p) => !p || p === '.' || p === '..')
  )
    throw new Error('包路径无效，请核对冻结清单。');
}
/** Resolve URI text once against its frozen logical location, never against a storage URL. */
export function resolvePackageReference(base: string, uri: string): string {
  if (!uri || uri.startsWith('/') || /[\\?#\x00-\x20\x7f]/.test(uri) || /^[a-z][a-z0-9+.-]*:/i.test(uri))
    throw new Error('模型引用了外部地址或无效依赖，不能直接预览。');
  const url = new URL(uri, 'https://collection-package.invalid/' + base.split('/').map(encodeURIComponent).join('/'));
  const path = decodeURIComponent(url.pathname.slice(1));
  requirePackagePath(path);
  return path;
}
