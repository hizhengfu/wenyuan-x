import request from '@/utils/request';
const base='/collection/acquisition';
export const acquisitionComplianceApi={
 get:(id:string)=>request({url:`${base}/${id}/compliance`,method:'get'}),
 dossier:(id:string,data:Record<string,any>)=>request({url:`${base}/${id}/dossier`,method:'post',data}),
 expert:(id:string,data:Record<string,any>)=>request({url:`${base}/${id}/expert`,method:'post',data}),
 completion:(id:string,data:Record<string,any>)=>request({url:`${base}/${id}/completion`,method:'post',data}),
};
