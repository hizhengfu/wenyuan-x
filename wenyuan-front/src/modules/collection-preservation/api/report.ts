/**
 * 环境质量评估报告 API
 * 后端路径：${apiPath}/preservation/report
 */
import request from '@/utils/request';
import type { PvReportCycle } from '../typings';

const BASE = '/preservation/report';

export interface GenerateReportRequest {
  orgId?: string;
  cycleType: PvReportCycle;
  periodStart: string;
  periodEnd: string;
  warehouseId?: string;
}

export const reportApi = {
  generationContext() {
    return request({ url: `${BASE}/generation-context`, method: 'get' });
  },
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  generate(body: GenerateReportRequest) {
    return request({ url: `${BASE}/generate`, method: 'post', data: body });
  },
  delete(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete' });
  },
};
