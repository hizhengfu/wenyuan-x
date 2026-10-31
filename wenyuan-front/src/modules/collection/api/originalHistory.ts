import request from '@/utils/request';
export interface OriginalHistoryItem {
  id: string;
  businessType: string;
  businessId: string;
  businessLabel?: string | null;
  orgId: string;
  orgName: string;
  name: string;
  retainedAt: string | number;
  contentIntegrity?: 'SHA256_RECORDED' | 'NOT_RECORDED';
  availability: 'AVAILABLE' | 'REMOTE_VERSION_RECORDED' | 'RECORDED_LOCATION' | 'IDENTITY_UNVERIFIED';
}
export const originalHistoryApi = {
  page: (
    params: {
      businessType?: string | null;
      businessId?: string;
      keyword?: string;
      pageNum: number;
      pageSize: number;
    },
    signal?: AbortSignal,
  ) => request({ url: '/collection/original-history', method: 'get', params, signal }),
  download: (id: string, signal?: AbortSignal) =>
    request({
      url: `/collection/original-history/${encodeURIComponent(id)}/download`,
      method: 'get',
      responseType: 'blob',
      signal,
      adapter: 'fetch',
      fetchOptions: { cache: 'no-store', redirect: 'error' },
    }) as Promise<Blob>,
};
export const originalBusinessNames: Record<string, string> = {
  COLLECTION_MAIN_FILES: '藏品主档材料',
  COLLECTION_ACQUISITION_PLAN: '征集计划',
  COLLECTION_LOAN: '借用',
  COLLECTION_REQUISITION: '提用',
  COLLECTION_RETURN: '归还',
  COLLECTION_DEREGISTER: '退出馆藏',
  COLLECTION_TRANSFER: '交换调拨',
  COLLECTION_CHANGE: '信息变更',
  COLLECTION_INVENTORY: '清点记录',
  COLLECTION_INVENTORY_BATCH: '清点批次',
  COLLECTION_APPRAISAL: '鉴定与专家意见',
  COLLECTION_REPRODUCTION: '复制拓印',
  COLLECTION_ACCESSION_RECEIPT: '入馆凭证',
  COLLECTION_EXHIBITION: '展览',
  COLLECTION_WAREHOUSE_ENTRY: '人员入库',
  COLLECTION_STORAGE_MOVEMENT: '出入库流水',
  COLLECTION_WAREHOUSE_DIARY: '库房日志',
  COLLECTION_WAREHOUSE_FILES: '库房资料',
  COLLECTION_WAREHOUSE_STAFFING: '库管任职',
  COLLECTION_ANNUAL_REPORT: '年度报告',
  COLLECTION_TRACKING_IMPORT: '动态跟踪导入',
  COLLECTION_INCIDENT_RELOCATION: '事故搬移',
  COLLECTION_RESTORATION: '修复项目',
  RESTORATION_CONTRACT_ORIGINAL: '修复合同',
  RESTORATION_PROCESS_ORIGINAL: '修复工序',
  RESTORATION_ACCEPTANCE_2026: '修复档案与验收',
  RESTORATION_QUALIFICATION_ORIGINAL: '修复资质证书',
  PRESERVATION_REPORT_ORIGINAL: '预防保护报告',
};
