import request from '@/utils/request';
export const periodReportApi = {
  preview(params: Record<string, unknown>, signal?: AbortSignal) { return request({url:'/collection/stat/period',method:'get',params,signal}) as Promise<{data:any}>; },
  history(params: Record<string, unknown>, signal?: AbortSignal) { return request({url:'/collection/stat/period/history',method:'get',params,signal}) as Promise<{data:any[]}>; },
  signed(data: Record<string, unknown>, signal?: AbortSignal) { return request({url:'/collection/stat/period/signed',method:'post',data,signal}); },
};
