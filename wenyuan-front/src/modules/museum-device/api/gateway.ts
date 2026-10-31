import request from '@/utils/request';
import type { GatewayVo, MuseumVenueVo } from '../typings';

const BASE = '/museum/device/gateway';

export const gatewayApi = {
  venues: () => request<MuseumVenueVo[]>({ url: `${BASE}/venues`, method: 'get' }),
  page: (params: Record<string, unknown>) => request({ url: `${BASE}/page`, method: 'get', params }),
  get: (id: string) => request({ url: `${BASE}/${id}`, method: 'get' }),
  create: (vo: GatewayVo) => request({ url: BASE, method: 'post', data: vo }),
  update: (id: string, vo: GatewayVo) => request({ url: `${BASE}/${id}`, method: 'put', data: vo }),
  regenerateToken: (id: string) => request({ url: `${BASE}/${id}/regenerate-token`, method: 'post' }),
  delete: (id: string) => request({ url: `${BASE}/${id}`, method: 'delete' }),
};
