import request from '@/utils/request';
import { saveAs } from 'file-saver';

export interface ExchangeItem { collectionId: string; data: { totalRegNo?: string; name?: string }; issues: string[]; photos: { mediaId: string; originalName?: string; exchangeName?: string; issues: string[] }[] }
export interface ExchangeCheck { valid: boolean; issueCount: number; items: ExchangeItem[] }
export const exchangeApi = {
  check(data: Record<string, unknown>, signal?: AbortSignal) {
    return request({ url: '/collection/exchange/check', method: 'post', data, signal }) as Promise<{ data: ExchangeCheck }>;
  },
  async download(data: Record<string, unknown>, signal?: AbortSignal) {
    const blob: unknown = await request({ url: '/collection/exchange/package', method: 'post', data, responseType: 'blob', signal,
      adapter: 'fetch', fetchOptions: { cache: 'no-store', redirect: 'error' } });
    signal?.throwIfAborted();
    if (!(blob instanceof Blob) || !['application/zip', 'application/octet-stream'].includes(blob.type.split(';')[0])) throw new Error('未返回有效交换包');
    const head = new Uint8Array(await blob.slice(0, 4).arrayBuffer());
    if (head[0] !== 80 || head[1] !== 75 || head[2] !== 3 || head[3] !== 4) throw new Error('交换包内容无效');
    signal?.throwIfAborted();
    saveAs(blob, '馆藏文物交换包.zip');
  },
};
