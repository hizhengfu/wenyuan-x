import request from '@/utils/request';

const BASE = '/museum/ingest/config/rfid';

export const museumRfidApi = {
  profiles: () => request({ url: `${BASE}/profiles`, method: 'get' }),
  connections: () => request({ url: `${BASE}/connections`, method: 'get' }),
  commands: (params?: any) => request({ url: `${BASE}/commands`, method: 'get', params }),
  command: (id: string) => request({ url: `${BASE}/commands/${id}`, method: 'get' }),
  createCommand: (data: any) => request({ url: `${BASE}/commands`, method: 'post', data }),
};
