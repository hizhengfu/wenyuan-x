/**
 * 藏品档案（总账 / 分类账 / 一物一档）API
 * 后端：io.hizhengfu.cloud.collection（路径前缀 ${apiPath}/collection）
 * 说明：request 会自动为相对路径拼接 APP_API_PATH（如 /api）。
 */
import request from '@/utils/request';
import type {
  CollectionAttachmentVo,
  CollectionImportRowVo,
  CollectionImportConfirmationVo,
  CollectionImportMode,
  CollectionWordDocumentType,
  CollectionFileImportItemVo,
  CollectionSetEntryVo,
  CollectionMediaVo,
  CollectionMediaFolderVo,
  CollectionSetVo,
  CollectionVo,
  DeregistrationVo,
} from '../typings/collection';

const BASE = '/collection';

export const collectionApi = {
  /** 分页查询（Service 自动按当前用户可见收藏单位过滤，单馆=默认馆） */
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  /**
   * 当前正式总账快照：条目、文物级别、类别及按单位数量等。
   * byCategory / byEra / byStatus / bySource / byWarehouse 等分布已内嵌于此；
   * 勿再请求不存在的 /collection/stat/by-*（会 404）。
   */
  statSummary(silentError = false) {
    return request({ url: `${BASE}/stat/summary`, method: 'get', silentError });
  },
  usageSummary() { return request({ url: `${BASE}/stat/usage-summary`, method: 'get' }); },
  /** 工作台：出入库按月趋势 */
  movementTrend(months = 12, silentError = false) {
    return request({ url: `${BASE}/stat/movement-trend`, method: 'get', params: { months }, silentError });
  },
  /** 工作台复用业务列表，失败由各区块展示，避免全局错误页中断其他数据。 */
  workbenchMovements() {
    return request({ url: `${BASE}/movement/page`, method: 'get', params: { pageNum: 1, pageSize: 6 }, silentError: true });
  },
  workbenchCatalogAudits(status: 'APPROVED' | 'REJECTED') {
    return request({ url: `${BASE}/catalog/page`, method: 'get', params: { pageNum: 1, pageSize: 6, status }, silentError: true });
  },
  /** 工作台：编目审核汇总（通过率） */
  auditSummary() {
    return request({ url: `${BASE}/stat/audit-summary`, method: 'get' });
  },
  /** 工作台：进行中的流程单据（最新动态） */
  ongoing(limit = 20) {
    return request({ url: `${BASE}/stat/ongoing`, method: 'get', params: { limit } });
  },
  /** 年度统计（年终上报：期初/新增/退出馆藏/期末/流动量） */
  annualStat(year: number) {
    return request({ url: `${BASE}/stat/annual`, method: 'get', params: { year } });
  },
  annualReports(year: number) { return request({url:`${BASE}/stat/annual/reports`,method:'get',params:{year}}); },
  signAnnualReport(data: any, expectedOrgId: string) {
    return request({ url: `${BASE}/stat/annual/reports`, method: 'post', params: { expectedOrgId }, data });
  },
  submitAnnualReport(id: string,data: any) { return request({url:`${BASE}/stat/annual/reports/${id}/submission`,method:'post',data}); },
  fileAnnualReport(id: string,data: any) { return request({url:`${BASE}/stat/annual/reports/${id}/filing`,method:'post',data}); },
  surveyStatus(params?: any) { return request({url:`${BASE}/stat/survey-status`,method:'get',params}); },
  annualArchive(year: number) {
    return request({ url: `${BASE}/stat/annual/archive`, method: 'get', params: { year } });
  },
  saveAnnualArchive(data: import('../typings/collection').AnnualReportArchiveVo) {
    return request({ url: `${BASE}/stat/annual/archive`, method: 'post', data });
  },
  /** RFID：绑定标签 */
  bindRfid(id: string, tag: string) {
    return request({ url: `${BASE}/${id}/rfid`, method: 'post', data: { tag } });
  },
  /** RFID：解绑标签 */
  unbindRfid(id: string) {
    return request({ url: `${BASE}/${id}/rfid`, method: 'delete' });
  },
  /** RFID：单标签解析（手持机单扫） */
  findByRfid(tag: string) {
    return request({ url: `${BASE}/rfid/${encodeURIComponent(tag)}`, method: 'get' });
  },
  /** RFID：批量扫描解析 */
  batchByRfid(tags: string[]) {
    return request({ url: `${BASE}/rfid/batch`, method: 'post', data: { tags } });
  },
  /** 普查导出：14 项基本指标列表（前端转 CSV） */
  surveyExport(params?: any) {
    return request({ url: `${BASE}/stat/survey-export`, method: 'get', params });
  },
  /** 已核查公开稿 feed；available=false 时消费方须移除旧内容。 */
  publishedFeed() {
    return request({ url: `${BASE}/published`, method: 'get' });
  },
  migrationVerificationBatches() { return request({ url: `${BASE}/registered-migration/verification-batches`, method: 'get' }); },
  migrationVerificationBatch(batchId: string) { return request({ url: `${BASE}/registered-migration/verification-batches/${encodeURIComponent(batchId)}`, method: 'get' }); },
  processMigrationVerificationBatch(data: unknown) { return request({ url: `${BASE}/registered-migration/verification-batches/process`, method: 'post', data }); },
  migrationTemplate() { return request({ url: `${BASE}/registered-migration/template`, method: 'get' }); },
  validateMigration(data: unknown) { return request({ url: `${BASE}/registered-migration/validate`, method: 'post', data }); },
  commitMigration(data: unknown, confirmation: CollectionImportConfirmationVo) {
    return request({ url: `${BASE}/registered-migration/commit`, method: 'post', data,
      params: { expectedOrgId: confirmation.orgId, expectedValidationHash: confirmation.validationHash } });
  },
  migrationVerification(id: string) { return request({ url: `${BASE}/registered-migration/${id}/verification`, method: 'get' }); },
  saveMigrationVerification(id: string, data: unknown, expectedStateHash: string, submit: boolean) {
    return request({ url: `${BASE}/registered-migration/${id}/verification`, method: 'post', data, params: { expectedStateHash, submit } });
  },
  withdrawMigrationVerification(id: string, expectedStateHash: string) {
    return request({ url: `${BASE}/registered-migration/${id}/verification/withdraw`, method: 'post', params: { expectedStateHash } });
  },
  reviewMigrationVerification(id: string, data: { approved: boolean; opinion: string }, expectedStateHash: string) {
    return request({ url: `${BASE}/registered-migration/${id}/verification/review`, method: 'post', data, params: { expectedStateHash } });
  },
  /** 批量导入模板字段 */
  importTemplate() {
    return request({ url: `${BASE}/import/template`, method: 'get' });
  },
  /** 批量导入预校验 */
  validateImport(rows: CollectionImportRowVo[], mode: CollectionImportMode = 'CREATE_ONLY', reason?: string) {
    return request({ url: `${BASE}/import/validate`, method: 'post', params: { mode, reason }, data: rows });
  },
  /** 按本次预校验确认批量导入 */
  commitImport(rows: CollectionImportRowVo[], mode: CollectionImportMode, reason: string | undefined, confirmation: CollectionImportConfirmationVo) {
    return request({ url: `${BASE}/import/commit`, method: 'post', params: { mode, reason, expectedOrgId: confirmation.orgId, expectedValidationHash: confirmation.validationHash }, data: rows });
  },
  /** 登记表/动态跟踪表 Word 结构识别与业务预校验（不写库）。 */
  validateWordImport(
    file: File,
    params: {
      mode?: CollectionImportMode;
      reason?: string;
      expectedCollectionId?: string;
      expectedDocumentType?: CollectionWordDocumentType;
    } = {},
  ) {
    const data = new FormData();
    data.append('file', file);
    return request({
      url: `${BASE}/word-import/validate`,
      method: 'post',
      params,
      data,
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  /** 核对本次预校验后导入，并把 Word 原件归入藏品附件。 */
  commitWordImport(
    file: File,
    params: {
      expectedOrgId: string;
      expectedValidationHash: string;
      mode?: CollectionImportMode;
      reason?: string;
      expectedCollectionId?: string;
      expectedDocumentType?: CollectionWordDocumentType;
    },
  ) {
    const data = new FormData();
    data.append('file', file);
    return request({
      url: `${BASE}/word-import/commit`,
      method: 'post',
      params,
      data,
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  wordImportHistory(collectionId: string) {
    return request({ url: `${BASE}/word-import/history/${collectionId}`, method: 'get' });
  },
  validateFileImport(items: CollectionFileImportItemVo[]) {
    return request({ url: `${BASE}/file-import/validate`, method: 'post', data: items });
  },
  commitFileImport(items: CollectionFileImportItemVo[], confirmation: CollectionImportConfirmationVo) {
    return request({ url: `${BASE}/file-import/commit`, method: 'post', data: items, params: { expectedOrgId: confirmation.orgId, expectedValidationHash: confirmation.validationHash } });
  },
  createSetEntry(vo: CollectionSetEntryVo) {
    return request({ url: `${BASE}/set-entry`, method: 'post', data: vo });
  },
  /** 一物一档详情 */
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  /** 登记 / 录入（待定 → 登账后进总账） */
  create(vo: CollectionVo) {
    return request({ url: BASE, method: 'post', data: vo });
  },
  /** 编辑非关键字段 */
  update(id: string, vo: CollectionVo) {
    return request({ url: `${BASE}/${id}`, method: 'put', data: vo });
  },
  /** 编目审核：APPROVED 通过并登账（写正式登记时间、计入总账），REJECTED 驳回待修改 */
  auditCatalog(id: string, status: 'APPROVED' | 'REJECTED', opinion: string | undefined, expectedStateHash: string) {
    return request({ url: `${BASE}/${id}/catalog-audit`, method: 'post', data: { status, opinion }, params: { expectedStateHash } });
  },
  /**
   * 关键字段更正（名称/级别/来源/取得日期等）：后端只建立信息变更申请草稿，返回变更单 id；
   * 审批执行后才改总账并写 col_ledger_log，调用方不得提示为「更正成功」。
   */
  correct(id: string, field: string, value: string, reason: string) {
    return request({ url: `${BASE}/${id}/correct`, method: 'post', data: { field, value, reason } });
  },
  /** 总账留痕 */
  ledgerLogs(id: string) {
    return request({ url: `${BASE}/${id}/ledger-logs`, method: 'get' });
  },
  /** 资料登记账 / 全局总账留痕 */
  ledgerLogPage(params: any) {
    return request({ url: `${BASE}/ledger-logs/page`, method: 'get', params });
  },
  media(id: string) {
    return request({ url: `${BASE}/${id}/media`, method: 'get' });
  },
  mediaStat() {
    return request({ url: `${BASE}/media/stat`, method: 'get' });
  },
  mediaFolders(id: string) {
    return request({ url: `${BASE}/${id}/media-folder`, method: 'get' });
  },
  saveMediaFolder(id: string, vo: CollectionMediaFolderVo) {
    return request({ url: `${BASE}/${id}/media-folder`, method: 'post', data: vo });
  },
  deleteMediaFolder(id: string, folderId: string) {
    return request({ url: `${BASE}/${id}/media-folder/${folderId}`, method: 'delete' });
  },
  saveMedia(id: string, vo: CollectionMediaVo) {
    return request({ url: `${BASE}/${id}/media`, method: 'post', data: vo });
  },
  deleteMedia(id: string, mediaId: string) {
    return request({ url: `${BASE}/${id}/media/${mediaId}`, method: 'delete' });
  },
  attachments(id: string) {
    return request({ url: `${BASE}/${id}/attachment`, method: 'get' });
  },
  saveAttachment(id: string, vo: CollectionAttachmentVo) {
    return request({ url: `${BASE}/${id}/attachment`, method: 'post', data: vo });
  },
  deleteAttachment(id: string, attachmentId: string) {
    return request({ url: `${BASE}/${id}/attachment/${attachmentId}`, method: 'delete' });
  },
  sets(id: string) {
    return request({ url: `${BASE}/${id}/set`, method: 'get' });
  },
  saveSet(id: string, vo: CollectionSetVo) {
    return request({ url: `${BASE}/${id}/set`, method: 'post', data: vo });
  },
  deleteSet(id: string, setId: string) {
    return request({ url: `${BASE}/${id}/set/${setId}`, method: 'delete' });
  },
  /** 退出馆藏申请（逐级报批，走审批流；通过后置 lifeStatus=DEREGISTERED，号不回收） */
  deregister(vo: DeregistrationVo) {
    return request({ url: `${BASE}/deregistration`, method: 'post', data: vo });
  },
  /** 仅启用当前已核查公开稿的 CMS 供稿，不记录实际官网或现场公开。 */
  publishCms(id: string) {
    return request({ url: `${BASE}/${id}/publish-cms`, method: 'post' });
  },
  // 注：藏品总账不可物理删除，故不提供 delete 接口。
};
