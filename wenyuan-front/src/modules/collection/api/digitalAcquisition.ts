import request from '@/utils/request';
const BASE = '/collection/digital-acquisition';
export const digitalAcquisitionApi = {
  detail: (id: string, signal?: AbortSignal) => request({ url: `${BASE}/${encodeURIComponent(id)}`, method: 'get', signal, adapter: 'fetch', fetchOptions: { cache: 'no-store', redirect: 'error' } }),
  record: (id: string, kind: string, data: Record<string, any>) =>
    request({ url: `${BASE}/${id}/${kind}`, method: 'post', data }),
  original: (id: string, fileId: string, signal?: AbortSignal) =>
    request({
      url: `${BASE}/${encodeURIComponent(id)}/file/${encodeURIComponent(fileId)}`,
      method: 'get',
      responseType: 'blob',
      signal,
      adapter: 'fetch',
      fetchOptions: { cache: 'no-store', redirect: 'error' },
      transformResponse: [
        (blob, headers) => {
          const disposition = String(headers['content-disposition'] || '');
          const encoded = /filename\*=utf-8''([^;]+)/i.exec(disposition)?.[1];
          let name: string | undefined;
          try {
            if (encoded) name = decodeURIComponent(encoded);
          } catch {
            /* 保留文件ID作为后备名称 */
          }
          const value = String(headers['x-collection-content-integrity'] || '');
          const integrity = value === 'sha256-verified' || value === 'not-recorded' ? value : undefined;
          return { blob, name, integrity };
        },
      ],
      timeout: 0,
    }),
};
