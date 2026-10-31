import request from '@/utils/request';
export interface WeeklyClosure { startOn: number; endOn: number; weekdays: number[]; remark?: string }
export interface OpeningException { date: number; open: boolean; reason: string }
export interface OpeningCalendarYear { year: number; revision: number; weeklyClosures: WeeklyClosure[]; exceptions: OpeningException[]; remark?: string }
export const openingCalendarApi = {
  get: (orgId: string, year: number) => request({ url: `/collection/opening-calendar/${orgId}/${year}`, method: 'get' }),
  save: (orgId: string, data: OpeningCalendarYear) => request({ url: `/collection/opening-calendar/${orgId}`, method: 'post', data }),
};
