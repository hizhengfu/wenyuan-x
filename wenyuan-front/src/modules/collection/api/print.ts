import request from '@/utils/request';
import { requireCollectionPrintBlob } from '../utils/printBlob';
import { normalizePrintError } from '../utils/printError';
import { saveAs } from 'file-saver';
import {
  buildCollectionPrintBatchFileName,
  buildCollectionPrintFileName,
  collectionPrintBatchFilePath,
  collectionPrintFilePath,
  type CollectionPrintFileFormat,
} from '../utils/printFile';
import type {
  CollectionPrintRenderRequest,
  CollectionPrintTemplateSaveVo,
  CollectionPrintTemplateUsageVo,
  CollectionPrintTemplateVo,
  CollectionPrintVo,
} from '../typings/collection';

const BASE = '/collection/print';

export const collectionPrintApi = {
  templates() {
    return request({ url: `${BASE}/templates`, method: 'get' }) as Promise<{ data: CollectionPrintTemplateVo[] }>;
  },
  template(type: string) {
    return request({ url: `${BASE}/templates/${type}`, method: 'get' }) as Promise<{ data: CollectionPrintTemplateVo }>;
  },
  saveTemplate(type: string, data: CollectionPrintTemplateSaveVo) {
    return request({ url: `${BASE}/templates/${type}`, method: 'put', data }) as Promise<{ data: string }>;
  },
  resetTemplate(type: string) {
    return request({ url: `${BASE}/templates/${type}`, method: 'delete' });
  },
  templateFields(type: string) {
    return request({ url: `${BASE}/templates/${type}/fields`, method: 'get' }) as Promise<{
      data: CollectionPrintTemplateUsageVo;
    }>;
  },
  data(type: string, id: string) {
    return request({ url: `${BASE}/${type}/${id}`, method: 'get' }) as Promise<{ data: CollectionPrintVo }>;
  },
  filePath(data: CollectionPrintVo, format: CollectionPrintFileFormat = 'PDF') {
    return collectionPrintFilePath(data, format);
  },
  batchFilePath(type: string, ids: string[], format: CollectionPrintFileFormat = 'PDF') {
    return collectionPrintBatchFilePath(type, ids, format);
  },
  async fileBlob(path: string, signal?: AbortSignal) {
    const parsed = new URL(path, 'https://collection-print.invalid');
    const decodedPath = decodeURIComponent(parsed.pathname);
    if (
      !path.startsWith('/collection/print/') ||
      parsed.origin !== 'https://collection-print.invalid' ||
      !decodedPath.startsWith('/collection/print/') ||
      parsed.hash ||
      decodedPath.includes('\\') ||
      decodedPath.split('/').some((part) => part === '..' || part === '.')
    )
      throw new Error('无效的打印文件入口');
    const format = /\.docx(?:[?]|$)/i.test(path) ? 'DOCX' : 'PDF';
    let blob: unknown;
    try {
      // silentError：错误体是 Blob，全局拦截器读不到后端原因；由调用方显示解析后的中文原因。
      blob = await request({
        url: path,
        method: 'get',
        responseType: 'blob',
        signal,
        adapter: 'fetch',
        fetchOptions: { cache: 'no-store', redirect: 'error' },
        silentError: true,
      });
    } catch (error) {
      throw await normalizePrintError(error);
    }
    signal?.throwIfAborted();
    return requireCollectionPrintBlob(blob, format);
  },
  async downloadPdf(data: CollectionPrintVo, signal?: AbortSignal) {
    const path = collectionPrintFilePath(data, 'PDF');
    if (!path) return Promise.resolve();
    const blob = await this.fileBlob(path, signal);
    signal?.throwIfAborted();
    saveAs(blob, buildCollectionPrintFileName(data, 'PDF'));
  },
  async downloadDocx(type: string, id: string, fileName?: string, signal?: AbortSignal) {
    const blob = await this.fileBlob(`${BASE}/${encodeURIComponent(type)}/${encodeURIComponent(id)}.docx`, signal);
    signal?.throwIfAborted();
    saveAs(blob, fileName || `${type}_${id}.docx`);
  },
  async downloadWord(data: CollectionPrintVo, signal?: AbortSignal) {
    const path = collectionPrintFilePath(data, 'DOCX');
    if (!path) return Promise.resolve();
    const blob = await this.fileBlob(path, signal);
    signal?.throwIfAborted();
    saveAs(blob, buildCollectionPrintFileName(data, 'DOCX'));
  },
  async downloadBatch(type: string, ids: string[], format: CollectionPrintFileFormat = 'PDF', signal?: AbortSignal) {
    const path = collectionPrintBatchFilePath(type, ids, format);
    if (!path) return Promise.resolve();
    const blob = await this.fileBlob(path, signal);
    signal?.throwIfAborted();
    saveAs(blob, buildCollectionPrintBatchFileName(type, ids.length, format));
  },
  async renderBlob(
    payload: CollectionPrintRenderRequest,
    format: CollectionPrintFileFormat = 'PDF',
    signal?: AbortSignal,
  ) {
    let blob: unknown;
    try {
      blob = await request({
        url: `${BASE}/render.${format === 'PDF' ? 'pdf' : 'docx'}`,
        method: 'post',
        data: payload,
        responseType: 'blob',
        signal,
        adapter: 'fetch',
        fetchOptions: { cache: 'no-store', redirect: 'error' },
        silentError: true,
      });
    } catch (error) {
      throw await normalizePrintError(error);
    }
    signal?.throwIfAborted();
    return requireCollectionPrintBlob(blob, format);
  },
  async downloadRender(
    payload: CollectionPrintRenderRequest,
    fileName: string,
    format: CollectionPrintFileFormat = 'PDF',
    signal?: AbortSignal,
  ) {
    const blob = await this.renderBlob(payload, format, signal);
    signal?.throwIfAborted();
    saveAs(blob, fileName);
  },
};
