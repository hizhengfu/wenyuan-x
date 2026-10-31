/** 库房日志 API（后端 ${apiPath}/collection/warehouse-log） */
import request from '@/utils/request';
import type { WarehouseLogVo } from '../typings/collection';

const BASE = '/collection/warehouse-log';

export const warehouseLogApi = {
  get(id:string){return request({url:`${BASE}/${id}`,method:'get'});},
  entries(warehouseId:string,logDate:number){return request({url:`${BASE}/entries`,method:'get',params:{warehouseId,logDate}});},
  gaps(from:number,to:number){return request({url:`${BASE}/gaps`,method:'get',params:{from,to}});},
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  save(vo: WarehouseLogVo) {
    return request({ url: BASE, method: 'post', data: vo });
  },
  delete(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete' });
  },
};
