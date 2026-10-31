// 相对部署以页面所在目录为根，不能把 index.html 拼进图片、下载和 API 地址。
const appBaseUrl = (APP_BASE_URL === '.' || APP_BASE_URL === ''
  ? new URL('.', location.href).href
  : APP_BASE_URL).replace(/\/+$/, '');
export const baseFilePath = appBaseUrl + '/userfiles';

/**
 * 构建api路径
 * */
export function apiUrl(path: string) {
  if (!path) return path;
  if (!(/https?/.test(path) || /\/\//.test(path))) {
    return appBaseUrl + APP_API_PATH + '/' + path.replace(/^\/+/, '');
  } else {
    return path;
  }
}

/**
 * 构建文件路径
 * */
export function fileUrl(path: string) {
  if (!path) return path;
  // 新业务保存 sys_file.id，雪花 ID 必须由预览接口解析。
  if (/^\d{15,}$/.test(path.trim())) {
    return apiUrl(`file/preview?fileId=${encodeURIComponent(path.trim())}`);
  }
  if (!(/https?/.test(path) || /\/\//.test(path))) {
    const normalizedBaseFilePath = baseFilePath.replace(/\/+$/, '');
    // publicUrl() 可能已经返回 /userfiles/...；fileUrl 必须保持幂等，避免重复拼成
    // /userfiles/userfiles/...。同时兼容数据库中保存的 images/... 原始相对路径。
    if (path === normalizedBaseFilePath || path.startsWith(`${normalizedBaseFilePath}/`)) {
      return path;
    }
    const fileBase = new URL(normalizedBaseFilePath, location.href);
    if (path === fileBase.pathname || path.startsWith(`${fileBase.pathname}/`)) {
      return new URL(path, fileBase).href;
    }
    const relativePath = path.replace(/^\.?\//, '').replace(/^userfiles\/+/, '');
    return normalizedBaseFilePath + '/' + relativePath;
  } else {
    return path;
  }
}

/**
 * 构建原始文件路径
 * */
export function originalFileUrl(path: string) {
  if (!path) return path;
  if (/^\d{15,}$/.test(path.trim())) return apiUrl(`file/original?fileId=${encodeURIComponent(path.trim())}`);
  const base = new URL(appBaseUrl + '/', location.href);
  const prefix = base.pathname.replace(/\/$/, '') + '/userfiles/';
  const absolute = /^(?:[a-z][a-z\d+.-]*:|\/\/)/i.test(path);
  const complete =
    path.startsWith(prefix) ||
    path.startsWith(base.pathname.replace(/\/$/, '') + APP_API_PATH.replace(/\/$/, '') + '/');
  const url = new URL(
    absolute || complete ? path : path.replace(/^\.?\//, '').replace(/^userfiles\//, ''),
    absolute ? location.href : complete ? base.origin : base.origin + prefix,
  );
  if (url.origin !== base.origin) return path;
  const previewPath = base.pathname.replace(/\/$/, '') + APP_API_PATH.replace(/\/$/, '') + '/file/preview';
  if (url.pathname === previewPath && url.searchParams.has('fileId')) {
    url.pathname = previewPath.replace(/preview$/, 'original');
    url.searchParams.delete('format');
    url.searchParams.delete('w');
    url.searchParams.delete('h');
    url.searchParams.delete('p');
    return url.href;
  }
  if (!url.pathname.startsWith(prefix)) return path;
  const relative = url.pathname.slice(prefix.length);
  url.pathname = prefix + (relative.startsWith('original/') ? relative : 'original/' + relative);
  return url.href;
}

/**
 * 文件扩展名
 * */
export function fileExt(path: string) {
  if (path.indexOf('.') === -1) return 'other';
  return path.split('.').pop();
}

/**
 * 构建链接路径
 * */
export function linkUrl(path: string) {
  if (!path) return path;
  let localPath = path;
  if (!(/https?/.test(path) || /\/\//.test(path))) {
    localPath = appBaseUrl + '/' + path.replace(/^\//, '');
  }
  return localPath;
}

/**
 * 将图片URL转换为base64
 */
async function urlToBase64(url: string) {
  try {
    const response = await fetch(url);
    const blob = await response.blob();
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onloadend = () => resolve(reader.result);
      reader.onerror = reject;
      reader.readAsDataURL(blob);
    });
  } catch (error) {
    console.error('图片加载失败:', url, error);
    return null;
  }
}
