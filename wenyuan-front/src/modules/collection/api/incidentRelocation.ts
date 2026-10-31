import request from '@/utils/request';
const BASE = '/collection/incident-relocation';
export const incidentRelocationApi = {
  list: (incidentId: string) => request({ url: BASE, method: 'get', params: { incidentId } }),
  context: (collectionId: string) => request({ url: `${BASE}/context`, method: 'get', params: { collectionId } }),
  detail: (id: string) => request({ url: `${BASE}/${id}`, method: 'get' }),
  create: (data: Record<string, any>) => request({ url: BASE, method: 'post', data }),
  emergency: (data: Record<string, any>) => request({ url: `${BASE}/emergency`, method: 'post', data }),
  record: (id: string, action: string, data: Record<string, any>) =>
    request({ url: `${BASE}/${id}/${action}`, method: 'post', data }),
};
