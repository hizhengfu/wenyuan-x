import request from '@/utils/request';
const BASE = '/collection/reproduction-compliance';
export const reproductionComplianceApi = {
  detail: (id: string) => request({ url: `${BASE}/${id}`, method: 'get' }),
  units: (id: string) => request({ url: `${BASE}/${id}/units`, method: 'get' }),
  record: (id: string, kind: string, data: Record<string, any>) =>
    request({ url: `${BASE}/${id}/${kind}`, method: 'post', data }),
  use: (id: string, output: string, data: Record<string, any>) =>
    request({ url: `${BASE}/${id}/output/${output}/use`, method: 'post', data }),
  custody: (id: string, output: string, data: Record<string, any>) =>
    request({ url: `${BASE}/${id}/output/${output}/custody`, method: 'post', data }),
  returned: (id: string, output: string, use: string, data: Record<string, any>) =>
    request({ url: `${BASE}/${id}/output/${output}/use/${use}/return`, method: 'post', data }),
};
