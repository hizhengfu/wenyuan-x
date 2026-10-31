/** 藏品归还/返还流程 API（${apiPath}/collection/return） */
import request from '@/utils/request';
import type { CollectionReturnVo } from '../typings/collection';

const BASE = '/collection/return';

export const collectionReturnApi = {
  inspection(id:string,data:any){return request({url:`${BASE}/${id}/inspection`,method:'post',data});},
  execute(id:string){return request({url:`${BASE}/${id}/execute`,method:'post'});},
  report(id:string,data:any,expectedInspectionArchiveId:string){return request({url:`${BASE}/${id}/report`,method:'post',params:{expectedInspectionArchiveId},data});},
  resolve(id:string,data:any,expectedOrgId:string){return request({url:`${BASE}/${id}/resolve`,method:'post',params:{expectedOrgId},data});},
  responsibility(id:string,expectedOrgId:string){return request({url:`${BASE}/${id}/responsibility`,method:'get',params:{expectedOrgId}});},
  registerResponsibility(id:string,data:any,expectedOrgId:string){return request({url:`${BASE}/${id}/responsibility`,method:'post',params:{expectedOrgId},data});},
  signResponsibility(id:string,recordId:string,role:string,data:any,expectedOrgId:string){return request({url:`${BASE}/${id}/responsibility/${recordId}/${role}`,method:'post',params:{expectedOrgId},data});},
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  save(vo: CollectionReturnVo) {
    return request({ url: `${BASE}/save`, method: 'post', data: vo });
  },
  ensureRequisition(id: string) {
    return request({ url: `${BASE}/requisition/${id}/ensure`, method: 'post' });
  },
  ensureLoan(id: string) {
    return request({ url: `${BASE}/loan/${id}/ensure`, method: 'post' });
  },
  submit(id: string) {
    return request({ url: `${BASE}/${id}/submit`, method: 'post' });
  },
  cancel(id: string) {
    return request({ url: `${BASE}/${id}/cancel`, method: 'post' });
  },
  voidRecord(id: string) {
    return request({ url: `${BASE}/${id}/void`, method: 'post' });
  },
  flow(id: string) {
    return request({ url: `${BASE}/${id}/flow`, method: 'get' });
  },
};
