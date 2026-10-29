import request from '@/utils/request';
import { isApplicationImageUrl } from './currentImage';

// 文档和图片使用同一组应用文件入口；不允许其他来源使用登录请求。
export const isApplicationDocumentUrl = isApplicationImageUrl;
export type CurrentDocument = { blob: Blob } | { html: string };

/** 只保留文档排版、文字和表格；不复制脚本、导航、表单或任何资源地址。 */
export function isolateDocumentHtml(html: string): string {
  const template = document.createElement('template');
  template.innerHTML = html;
  if (template.content.querySelector('form, input, select, textarea') || !template.content.querySelector('table, p'))
    throw new Error('文档预览返回了非文档页面');
  const allowed = new Set(
    'div span p h1 h2 h3 h4 h5 h6 table thead tbody tfoot tr th td caption colgroup col b strong i em u s pre code ul ol li br hr blockquote'.split(
      ' ',
    ),
  );
  const excluded = new Set(
    'script style iframe object embed svg math template noscript head title meta link base form input button textarea select'.split(
      ' ',
    ),
  );
  function copy(source: Node, target: Node) {
    if (source.nodeType === Node.TEXT_NODE) {
      target.appendChild(document.createTextNode(source.textContent || ''));
      return;
    }
    if (!(source instanceof HTMLElement)) return;
    const tag = source.tagName.toLowerCase();
    if (excluded.has(tag)) return;
    const next = allowed.has(tag) ? document.createElement(tag) : document.createDocumentFragment();
    if (next instanceof HTMLElement) {
      for (const attr of ['colspan', 'rowspan']) {
        const value = source.getAttribute(attr);
        if ((tag === 'td' || tag === 'th') && value && /^[1-9]\d{0,3}$/.test(value)) next.setAttribute(attr, value);
      }
    }
    source.childNodes.forEach((child) => copy(child, next));
    target.appendChild(next);
  }
  const body = document.createElement('div');
  template.content.childNodes.forEach((node) => copy(node, body));
  return `<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'"><style>body{font:14px sans-serif;color:#333;padding:16px}table{border-collapse:collapse;margin-bottom:20px}td,th{border:1px solid #ccc;padding:5px 10px;white-space:pre-wrap}th{background:#f0f4f8}pre{white-space:pre-wrap}</style></head><body>${body.innerHTML}</body></html>`;
}

export async function readCurrentDocument(source: string, kind: string, signal: AbortSignal): Promise<CurrentDocument> {
  if (!isApplicationDocumentUrl(source)) throw new Error('非应用文档不能使用登录请求');
  const url = new URL(source, window.location.href);
  const result: unknown = await request({
    url: url.href,
    method: 'get',
    responseType: 'blob',
    signal,
    adapter: 'fetch',
    fetchOptions: { cache: 'no-store', redirect: 'error' },
  });
  if (!(result instanceof Blob) || !result.size) throw new Error('文档读取结果为空');
  const mime = result.type.split(';')[0].trim().toLowerCase();
  const base = new URL(APP_BASE_URL || '.', window.location.href.split('#')[0]);
  const isPreviewApi = url.pathname === base.pathname.replace(/\/$/, '') + APP_API_PATH + '/file/preview';
  // Excel 转换器只输出 HTML；不能把登录页或直接的原始 Office 文件作为 PDF 展示。
  const expectsHtml =
    isPreviewApi && (['xls', 'xlsx'].includes(kind) || url.searchParams.get('format')?.toLowerCase() === 'html');
  if (expectsHtml && mime === 'text/html') return { html: isolateDocumentHtml(await result.text()) };
  if (
    !['application/pdf', 'application/octet-stream'].includes(mime) ||
    !(await result.slice(0, 5).text()).startsWith('%PDF-')
  )
    throw new Error('未返回可预览的文档，请核对转换入口及原文件');
  return { blob: mime === 'application/pdf' ? result : new Blob([result], { type: 'application/pdf' }) };
}
