import request from '@/utils/request';
export const businessList = (params: any) => request({ url: 'resource/business/list', params });
export const businessGet = (id: string) => request({ url: `resource/business/${id}` });
export const businessSave = (data: any) => request({ url: 'resource/business', method: 'post', data });
export const businessAction = (id: string, action: string, data?: any) =>
  request({ url: `resource/business/${id}/${action}`, method: 'post', data });
export const businessFlow = (id: string) => request({ url: `resource/business/${id}/flow` });
export const timeline = (id: string) => request({ url: `resource/business/${id}/timeline` });
export const businessEvents = (id: string) => request({ url: `resource/business/${id}/events` });
export const certificates = (planId?: string) => request({ url: 'resource/business/certificates', params: { planId } });
export const research = () => request({ url: 'resource/business/research' });
export const sales = (params: any) => request({ url: 'resource/business/sales', params });
export const importSales = (data: any[]) => request({ url: 'resource/business/sales/import', method: 'post', data });
export const voidSale = (id: string, reason: string) =>
  request({ url: `resource/business/sales/${id}/void`, method: 'post', data: { reason } });
export const insights = (year?: number) => request({ url: 'resource/business/insights', params: { year } });
export const publications = (subjectType: string, subjectId: string) =>
  request({ url: 'resource/business/publications', params: { subjectType, subjectId } });
export const publishChannel = (data: any) => request({ url: 'resource/business/publications', method: 'post', data });
export const channelFeed = (channel: string, page = 1) =>
  request({ url: `front/resource/channels/${channel}`, params: { page } });
export const publicSubject = (type: string, id: string) =>
  request({ url: `front/resource/channels/subject/${type}/${id}` });
export const publicResource = (id: string) => request({ url: `front/resource/${id}` });
export const workbench = () => request({ url: 'resource/business/workbench' });
export const collectionOptions = (keyword = '') =>
  request({ url: 'resource/business/collections', params: { keyword } });
export const templates = () => request({ url: 'resource/catalog/templates' });
export const saveTemplate = (data: any) => request({ url: 'resource/catalog/templates', method: 'post', data });
export const deleteTemplate = (id: string) => request({ url: `resource/catalog/templates/${id}`, method: 'delete' });
export const usageLogs = (params: any, exporting = false) =>
  request({ url: `resource/catalog/usage${exporting ? '/export' : ''}`, params });
export const PURPOSES = [
  { label: '学术研究', value: 'RESEARCH' },
  { label: '数字教学', value: 'TEACHING' },
  { label: '展览展示', value: 'EXHIBITION' },
  { label: '保护修复', value: 'CONSERVATION' },
  { label: '宣传推广', value: 'PROMOTION' },
  { label: '其他', value: 'OTHER' },
];
export const PRODUCT_CATEGORIES = [
  { label: '文物复制品', value: 'REPLICA' },
  { label: '文具', value: 'STATIONERY' },
  { label: '服饰配饰', value: 'APPAREL' },
  { label: '数字藏品', value: 'DIGITAL' },
  { label: '其他', value: 'OTHER' },
];
export const KIND_NAMES: Record<string, string> = {
  SCENE: '文化景观',
  CAPTURE: '景观采集',
  PLAN: '数字收藏征集计划',
  PRODUCT: '文创产品',
  LICENSE: '厂商授权与知识产权',
};
