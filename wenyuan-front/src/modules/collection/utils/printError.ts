/**
 * 打印文件（blob）请求失败时的提示文字。
 * responseType=blob 时后端的 JSON 错误体也是 Blob，需要先读成文本再解析，
 * 才能拿到 msg / message；拿不到时只显示中文兜底，不显示 axios 的英文通用报错。
 */
const HAS_CJK = /[\u3400-\u9fff\uf900-\ufaff]/;

export const PRINT_ERROR_FALLBACK = '打印文件生成失败，请核对当前授权与数据后重试';

function isBlobLike(value: unknown): value is Blob {
  return Boolean(value) && typeof value === 'object' && typeof (value as Blob).text === 'function' && typeof (value as Blob).size === 'number';
}

function pickMessage(body: unknown): string {
  if (!body || typeof body !== 'object') return '';
  const data = body as Record<string, unknown>;
  for (const key of ['msg', 'message']) {
    const value = data[key];
    if (typeof value === 'string' && value.trim()) return value.trim();
  }
  return '';
}

/** 解析错误响应体：Blob/字符串按 JSON 解析，失败返回原文本；对象原样返回。 */
export async function readPrintErrorBody(data: unknown): Promise<unknown> {
  let text: string;
  if (isBlobLike(data)) {
    try {
      text = await data.text();
    } catch {
      return undefined;
    }
  } else if (typeof data === 'string') text = data;
  else return data;
  const trimmed = text.trim();
  if (!trimmed) return undefined;
  try {
    return JSON.parse(trimmed);
  } catch {
    return trimmed;
  }
}

/** 返回给用户看的中文原因；后端 msg/message 优先。 */
export async function printErrorText(error: unknown, fallback = PRINT_ERROR_FALLBACK): Promise<string> {
  const e = error as any;
  if (e?.response) {
    const body = await readPrintErrorBody(e.response.data);
    const server = pickMessage(body);
    if (server) return server;
    // 纯文本错误体只在是中文提示时显示，避免把 HTML 错误页或英文堆栈给用户。
    if (typeof body === 'string' && body.length <= 200 && HAS_CJK.test(body) && !/<[a-z!/]/i.test(body)) return body;
    return fallback;
  }
  const message = typeof e?.message === 'string' ? e.message.trim() : typeof error === 'string' ? error.trim() : '';
  return message && HAS_CJK.test(message) ? message : fallback;
}

function isCanceled(error: unknown) {
  const e = error as any;
  return Boolean(e?.__CANCEL__) || e?.name === 'CanceledError' || e?.name === 'AbortError' || e?.code === 'ERR_CANCELED';
}

/** 把打印请求的错误换成带中文原因的 Error；取消类错误原样返回，便于调用方静默处理。 */
export async function normalizePrintError(error: unknown, fallback = PRINT_ERROR_FALLBACK): Promise<unknown> {
  if (isCanceled(error)) return error;
  const normalized = new Error(await printErrorText(error, fallback));
  (normalized as Error & { cause?: unknown; status?: number }).cause = error;
  const status = (error as any)?.response?.status;
  if (typeof status === 'number') (normalized as Error & { status?: number }).status = status;
  return normalized;
}
