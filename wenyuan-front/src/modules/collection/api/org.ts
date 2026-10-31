/**
 * 收藏单位（多馆基础）API
 * 后端路径：${apiPath}/collection/org
 */
import request from '@/utils/request';
import type { CollectionOrgImportRowVo, CollectionOrgOverviewNode, CollectionOrgVo } from '../typings/collection';

const BASE = '/collection/org';

export const orgApi = {
  codeContext(id: string) { return request({ url: `${BASE}/${id}/code-context`, method: 'get' }); },
  correctCode(id: string, data: { newCode: string; reason: string }, expectedStateHash: string) {
    return request({ url: `${BASE}/${id}/code-corrections`, method: 'post', data, params: { expectedStateHash } });
  },
  businessContext() { return request({ url: `${BASE}/business-context`, method: 'get' }); },
  paperBooks(orgId:string) {return request({url:`${BASE}/${orgId}/paper-ledger`,method:'get'});},
  registerPaperBook(orgId:string,data:any) {return request({url:`${BASE}/${orgId}/paper-ledger`,method:'post',data});},
  recordPaperCustody(orgId:string,id:string,data:any) {return request({url:`${BASE}/${orgId}/paper-ledger/${id}/custody`,method:'post',data});},
  reconciliationCollections(orgId:string) {return request({url:`${BASE}/${orgId}/paper-ledger/reconciliation-collections`,method:'get'});},
  ledgerReconciliations(orgId:string) {return request({url:`${BASE}/${orgId}/paper-ledger/reconciliations`,method:'get'});},
  recordLedgerReconciliation(orgId:string,data:any) {return request({url:`${BASE}/${orgId}/paper-ledger/reconciliations`,method:'post',data});},

  managementRules(orgId: string) { return request({url:`${BASE}/${orgId}/management-rules`,method:'get'}); },
  registerManagementRules(orgId:string,data:any) { return request({url:`${BASE}/${orgId}/management-rules`,method:'post',data}); },
  submitManagementRules(orgId:string,id:string,data:any) { return request({url:`${BASE}/${orgId}/management-rules/${id}/submission`,method:'post',data}); },
  fileManagementRules(orgId:string,id:string,data:any) { return request({url:`${BASE}/${orgId}/management-rules/${id}/filing`,method:'post',data}); },

  /** 总分馆树 */
  tree() {
    return request({ url: `${BASE}/tree`, method: 'get' });
  },
  overview() {
    return request<CollectionOrgOverviewNode[]>({ url: `${BASE}/overview`, method: 'get' });
  },
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  /** 全量列表（用于下拉选择当前馆） */
  list() {
    return request({ url: BASE, method: 'get' });
  },
  importTemplate() {
    return request({ url: `${BASE}/import/template`, method: 'get' });
  },
  validateImport(rows: CollectionOrgImportRowVo[]) {
    return request({ url: `${BASE}/import/validate`, method: 'post', data: rows });
  },
  commitImport(rows: CollectionOrgImportRowVo[]) {
    return request({ url: `${BASE}/import/commit`, method: 'post', data: rows });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  create(vo: CollectionOrgVo) {
    return request({ url: BASE, method: 'post', data: vo });
  },
  update(id: string, vo: CollectionOrgVo) {
    return request({ url: `${BASE}/${id}`, method: 'put', data: vo });
  },
  sort(data: { id: string; sort: number }[]) {
    return request({ url: `${BASE}/sort`, method: 'post', data });
  },
  delete(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete' });
  },
};
