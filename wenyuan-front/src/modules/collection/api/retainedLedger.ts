import request from '@/utils/request';
export const retainedLedgerApi={
 page:(params:any)=>request({url:'/collection/retained-ledger/page',method:'get',params}),
 get:(id:string)=>request({url:`/collection/retained-ledger/${id}`,method:'get'}),
};
