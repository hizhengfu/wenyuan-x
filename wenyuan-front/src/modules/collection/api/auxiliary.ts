import request from '@/utils/request';
export const auxiliaryApi={
 list:(orgId:string)=>request({url:`/collection/auxiliary/${orgId}`,method:'get'}),
 detail:(orgId:string,id:string)=>request({url:`/collection/auxiliary/${orgId}/${id}`,method:'get'}),
 register:(data:Record<string,any>)=>request({url:'/collection/auxiliary',method:'post',data}),
};
