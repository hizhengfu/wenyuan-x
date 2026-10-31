/** 修复项目 API（${apiPath}/collection/restoration） */
import request from '@/utils/request';
import type { RestorationVo } from '../typings';

const BASE = '/collection/restoration';

export interface RestorationDraftContext {
  id: string | null;
  orgId: string;
  orgName: string;
  collectionId: string;
  collection: Record<string, any>;
  project: Record<string, any> | null;
  revision: string;
  collectionRevision: string;
  canEdit: boolean;
  issue?: string;
}
interface DraftConfirmation {
  id?: string;
  collectionId: string;
  expectedOrgId: string;
  expectedRevision: string;
  expectedCollectionRevision: string;
}

export const restorationApi = {
  workSummary: () => request({ url: `${BASE}/work-summary`, method: 'get' }),
  replacementInfo: (id: string) => request({ url: `${BASE}/${id}/responsibility-replacement`, method: 'get' }),
  replaceResponsibility: (id: string, data: Record<string, any>) => request({ url: `${BASE}/${id}/responsibility-replacement`, method: 'post', data }),
  compliance: (id: string) => request({ url: `${BASE}/${id}/compliance`, method: 'get' }),
  planEvidence: (id: string, data: Record<string, any>, expectedOrgId:string) =>
    request({ url: `${BASE}/${id}/plan-evidence`, method: 'post', params:{expectedOrgId}, data }),
  administrative: (id: string, data: Record<string, any>, expectedOrgId:string) =>
    request({ url: `${BASE}/${id}/administrative`, method: 'post', params:{expectedOrgId}, data }),
  sentStop: (id: string, data: Record<string, any>, expectedOrgId:string) =>
    request({ url: `${BASE}/${id}/sent-stop`, method: 'post', params:{expectedOrgId}, data }),
  stop: (id: string, data: Record<string, any>, expectedOrgId:string) => request({ url: `${BASE}/${id}/stop`, method: 'post', params:{expectedOrgId}, data }),
  continuation: (id: string, data: Record<string, any>, expectedOrgId:string, expectedRevision:string) =>
    request({ url: `${BASE}/${id}/continuation`, method: 'post', params:{expectedOrgId,expectedRevision}, data }),
  safetyCheck: (id: string, data: Record<string, any>, expectedOrgId:string, expectedPlanId:string) =>
    request({ url: `${BASE}/${id}/safety-check`, method: 'post', params:{expectedOrgId,expectedPlanId}, data }),
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  draftContext(collectionId?: string, id?: string) {
    return request({ url: `${BASE}/draft-context`, method: 'get', params: { collectionId, id } });
  },
  async create(vo: RestorationVo & { __draftConfirmation?: DraftConfirmation }) {
    const { __draftConfirmation: fixed, ...data } = vo;
    if (!fixed || fixed.id !== (data.id || undefined) || fixed.collectionId !== data.collectionId
        || [fixed.expectedOrgId, fixed.expectedRevision, fixed.expectedCollectionRevision].some(v => typeof v !== 'string' || !v.trim())) {
      throw new Error('修复草稿缺少打开时的确认资料，请关闭后重新打开');
    }
    const response: any = await request({ url: BASE, method: 'post', data, params: {
      expectedOrgId: fixed.expectedOrgId, expectedRevision: fixed.expectedRevision, expectedCollectionRevision: fixed.expectedCollectionRevision,
    } });
    const id = response?.data?.data ?? response?.data;
    if ((response?.status !== undefined && response.status !== 200)
        || (response?.data?.status !== undefined && response.data.status !== 200)
        || typeof id !== 'string' || !id.trim() || (fixed.id && id !== fixed.id)) {
      throw new Error('修复草稿保存结果无法确认，请重新查询项目列表');
    }
    return response;
  },
  submit(id: string) {
    return request({ url: `${BASE}/${id}/submit`, method: 'post' });
  },
  cancel(id: string) {
    return request({ url: `${BASE}/${id}/cancel`, method: 'post' });
  },
  void_(id: string) {
    return request({ url: `${BASE}/${id}/void`, method: 'post' });
  },
  start(id: string, data: Record<string, any>,expectedOrgId:string) {
    return request({ url: `${BASE}/${id}/start`, method: 'post', params:{expectedOrgId}, data });
  },
  complete(id: string, data: Record<string, any>,expectedOrgId:string,expectedPlanId:string) {
    return request({ url: `${BASE}/${id}/complete`, method: 'post', params:{expectedOrgId,expectedPlanId}, data });
  },

  print(id: string) {
    return request({ url: `${BASE}/${id}/print`, method: 'get' });
  },
  flow(id: string) {
    return request({ url: `${BASE}/${id}/flow`, method: 'get' });
  },
};
