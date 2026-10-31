/** 图片引用只能是文件 ID、图片路径或 URL，不能把导入模板中的说明文字拼成文件请求。 */
export function normalizeImageReference(value?: string | null): string | undefined {
  const reference = value?.trim();
  if (!reference) return undefined;
  if (/^(?:data:image\/|blob:)/i.test(reference)) return reference;

  let path = reference.split(/[?#]/)[0];
  if (/^(?:https?:)?\/\//i.test(reference)) {
    try {
      const url = new URL(reference, 'https://image.invalid');
      // HTTP 图片可能使用没有扩展名的接口；应用文件入口仍须校验文件名或 ID。
      if (!url.pathname.includes('/userfiles/')) return reference;
      path = url.pathname;
    } catch {
      return undefined;
    }
  }
  try {
    path = decodeURIComponent(path);
  } catch {
    return undefined;
  }
  const name = path.split('/').pop() || '';
  return /^\d+$/.test(name) ||
    /^(?:[a-f\d]{32}|[a-f\d]{8}(?:-[a-f\d]{4}){3}-[a-f\d]{12})$/i.test(name) ||
    /\.(?:jpe?g|jfif|png|apng|gif|webp|bmp|svg|ico|avif|tiff?|heic|heif)$/i.test(name)
    ? reference
    : undefined;
}
