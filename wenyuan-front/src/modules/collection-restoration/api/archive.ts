/** 修复档案 / 病害 / 验收 三合一 API（${apiPath}/collection/restoration-archive） */
import request from '@/utils/request';
import type { RestorationArchiveVo, RestorationDamageVo, RestorationAcceptanceVo } from '../typings';

const BASE = '/collection/restoration-archive';

export const restorationArchiveApi = {
  context(restorationId: string) {
    return request({ url: `${BASE}/context/${restorationId}`, method: 'get' });
  },
  /** 档案列表分页（跨修复项目） */
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  getById(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  print(id: string) {
    return request({ url: `${BASE}/${id}/print`, method: 'get' });
  },
  delete(id: string, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete', params: { expectedOrgId, expectedRevision } });
  },

  /** 档案主件（by restorationId） */
  getByRestoration(restorationId: string) {
    return request({ url: `${BASE}/by-restoration/${restorationId}`, method: 'get' });
  },
  save(vo: RestorationArchiveVo, expectedOrgId: string, expectedRevision: string) {
    return request({ url: BASE, method: 'post', data: vo, params: { expectedOrgId, expectedRevision } });
  },
  approve(id: string, data: Record<string, any>, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/${id}/approve`, method: 'post', data, params: { expectedOrgId, expectedRevision } });
  },

  /** 病害记录 */
  damagePage(params: any) {
    return request({ url: `${BASE}/damage/page`, method: 'get', params });
  },
  damageList(restorationId: string) {
    return request({ url: `${BASE}/damage/list/${restorationId}`, method: 'get' });
  },
  damageSave(vo: RestorationDamageVo, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/damage`, method: 'post', data: vo, params: { expectedOrgId, expectedRevision } });
  },
  damageDelete(id: string, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/damage/${id}`, method: 'delete', params: { expectedOrgId, expectedRevision } });
  },

  /** 验收单 */
  acceptancePage(params: any) {
    return request({ url: `${BASE}/acceptance/page`, method: 'get', params });
  },
  acceptanceGetById(id: string) {
    return request({ url: `${BASE}/acceptance/id/${id}`, method: 'get' });
  },
  acceptanceGet(restorationId: string) {
    return request({ url: `${BASE}/acceptance/${restorationId}`, method: 'get' });
  },
  acceptanceSave(vo: RestorationAcceptanceVo) {
    return request({ url: `${BASE}/acceptance`, method: 'post', data: vo });
  },
  acceptanceDelete(id: string) {
    return request({ url: `${BASE}/acceptance/${id}`, method: 'delete' });
  },
};
