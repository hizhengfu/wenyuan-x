/**
 * 界面错误文字：
 * 1. 优先显示后端返回的 message（与项目内 e?.response?.data?.message 的处理方式一致）；
 * 2. axios 的通用英文报错（Request failed with status code …、Network Error、timeout、canceled）不直接展示，改用调用方给的中文兜底；
 * 3. 前端主动抛出的中文业务错误照常显示；
 * 4. 其他非 axios 的运行时错误（TypeError、ReferenceError 等，或不含中文的英文 message）不把英文原文给用户，统一显示 RUNTIME_FALLBACK。
 */
const GENERIC = /^(Request failed with status code \d+|Network Error|timeout of \d+ms exceeded|canceled)$/i;
/** JS 引擎内置运行时错误类型：即便 message 被翻译，也属于程序错误，不直接展示。 */
const RUNTIME_ERRORS = new Set(['TypeError', 'ReferenceError', 'RangeError', 'SyntaxError', 'URIError', 'EvalError', 'InternalError']);
const HAS_CJK = /[\u3400-\u9fff\uf900-\ufaff]/;

export const RUNTIME_FALLBACK = '操作失败，请稍后重试';

export function errorText(error: unknown, fallback: string): string {
  const e = error as any;
  const data = e?.response?.data;
  const server = typeof data === 'string' ? '' : data?.message ?? data?.msg;
  if (typeof server === 'string' && server.trim()) return server.trim();
  // axios 错误（或带 response 的请求错误）没有后端 message：用调用方的中文兜底。
  if (e?.isAxiosError || e?.response) return fallback;
  if (typeof error === 'string') return HAS_CJK.test(error) ? error.trim() : RUNTIME_FALLBACK;
  const message = typeof e?.message === 'string' ? e.message.trim() : '';
  if (!message) return fallback;
  if (GENERIC.test(message)) return fallback;
  if (RUNTIME_ERRORS.has(String(e?.name ?? ''))) return RUNTIME_FALLBACK;
  return HAS_CJK.test(message) ? message : RUNTIME_FALLBACK;
}
