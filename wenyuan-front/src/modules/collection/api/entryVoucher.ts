/** 入馆凭证 API（后端 ${apiPath}/collection/entry-voucher） */
import request from '@/utils/request';
import type { EntryVoucherVo } from '../typings/collection';

const BASE = '/collection/entry-voucher';

export interface EntryVoucherRegistrationTarget {
  collectionId: string;
  totalRegNo: string;
  name: string;
  stateHash: string;
}

export interface EntryVoucherExecuteRequest {
  existingCollections: Record<string, { collectionId: string; stateHash: string }>;
}

export const entryVoucherApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  /** 可入凭证的征集单（审批通过 + 未登账 + 未被其他凭证占用） */
  registrable() {
    return request({ url: `${BASE}/registrable`, method: 'get' });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  receipts(id: string) {
    return request({ url: `${BASE}/${id}/receipts`, method: 'get' });
  },
  recordReceipt(id: string, data: any) {
    return request({ url: `${BASE}/${id}/receipts`, method: 'post', data });
  },
  print(id: string) {
    return request({ url: `${BASE}/${id}/print`, method: 'get' });
  },
  save(vo: EntryVoucherVo) {
    return request({ url: `${BASE}/save`, method: 'post', data: vo });
  },
  delete(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete' });
  },
  /** 依据已核收原件生成待登记记录 */
  registrationTargets(id: string) {
    return request({ url: `${BASE}/${id}/registration-targets`, method: 'get' });
  },
  execute(id: string, data?: EntryVoucherExecuteRequest) {
    return request({ url: `${BASE}/${id}/execute`, method: 'post', data });
  },
};
