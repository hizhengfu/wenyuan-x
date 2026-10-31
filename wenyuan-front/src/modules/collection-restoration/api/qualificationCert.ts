/** 资质证书 API（${apiPath}/collection/qualification-cert） */
import request from '@/utils/request';
import type { QualificationCertVo } from '../typings';

const BASE = '/collection/qualification-cert';

export const qualificationCertApi = {
  context(id?: string) {
    return request({ url: `${BASE}/context`, method: 'get', params: { id } });
  },
  holderContext(holderType: string, holderId: string, expectedOrgId: string) {
    return request({ url: `${BASE}/holder-context`, method: 'get', params: { holderType, holderId, expectedOrgId } });
  },
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  byHolder(holderType: string, holderId: string) {
    return request({ url: `${BASE}/by-holder`, method: 'get', params: { holderType, holderId } });
  },
  expiring(days = 90) {
    return request({ url: `${BASE}/expiring`, method: 'get', params: { days } });
  },
  save(vo: QualificationCertVo, expectedOrgId: string, expectedRevision: string, expectedHolderRevision: string) {
    return request({ url: BASE, method: 'post', data: vo, params: { expectedOrgId, expectedRevision, expectedHolderRevision } });
  },
  delete(id: string, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete', params: { expectedOrgId, expectedRevision } });
  },
};
