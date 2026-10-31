import request from '@/utils/request';
const BASE = '/preservation/legacy-thresholds';
export const legacyThresholdApi = {
  preview: () => request({ url: BASE, method: 'get' }),
  create: (data: { sourceHash: string; name: string }) => request({ url: BASE, method: 'post', data }),
};
