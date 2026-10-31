import request from '@/utils/request';
export interface CalendarYear { year:number; holidays:number[]; extraWorkdays:number[]; sourceTitle:string; sourceUrl:string; evidenceFileIds?:string }
export const calendarApi = {
  get:(orgId:string, year:number) => request({url:`/collection/calendar/${orgId}/${year}`,method:'get'}),
  save:(orgId:string, data:CalendarYear) => request({url:`/collection/calendar/${orgId}`,method:'post',data}),
};
