/** 借展 API（流程驱动，${apiPath}/collection/loan） */
import request from '@/utils/request';
import type { CollectionOutboundExecutionVo, LoanVo } from '../typings/collection';

const BASE = '/collection/loan';

export const loanApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  get(id: string, signal?: AbortSignal) {
    return request({ url: `${BASE}/${id}`, method: 'get', signal });
  },
  save(vo: LoanVo) {
    return request({ url: `${BASE}/save`, method: 'post', data: vo });
  },
  submit(id: string) {
    return request({ url: `${BASE}/${id}/submit`, method: 'post' });
  },
  cancel(id: string) {
    return request({ url: `${BASE}/${id}/cancel`, method: 'post' });
  },
  administrative(id:string,data:Record<string,any>){return request({url:`${BASE}/${id}/administrative`,method:'post',data});},
  handover(id:string,data:Record<string,any>){return request({url:`${BASE}/${id}/handover`,method:'post',data});},
  monitoring(id:string,data:Record<string,any>){return request({url:`${BASE}/${id}/monitoring`,method:'post',data});},
  exhibitionEnd(id:string,expectedOrgId:string,data:Record<string,any>){return request({url:`${BASE}/${id}/exhibition-end`,method:"post",params:{expectedOrgId},data});},
  overseasFiling(id:string,expectedOrgId:string,data:Record<string,any>){return request({url:`${BASE}/${id}/overseas-filing`,method:"post",params:{expectedOrgId},data});},
  port(id:string,expectedOrgId:string,data:Record<string,any>){return request({url:`${BASE}/${id}/overseas-port`,method:"post",params:{expectedOrgId},data});},
  extension(id:string,expectedOrgId:string,data:Record<string,any>){return request({url:`${BASE}/${id}/overseas-extension`,method:'post',params:{expectedOrgId},data});},
  executeOut(id: string, data: CollectionOutboundExecutionVo) {
    return request({ url: `${BASE}/${id}/execute-out`, method: 'post', data });
  },
  /** 返还核对（审批通过后） */
  returnBack(id: string) {
    return request({ url: `${BASE}/${id}/return`, method: 'post' });
  },
  print(id: string) {
    return request({ url: `${BASE}/${id}/print`, method: 'get' });
  },
  flow(id: string) {
    return request({ url: `${BASE}/${id}/flow`, method: 'get' });
  },
};
