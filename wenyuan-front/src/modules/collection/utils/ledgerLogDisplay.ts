import { isEqual } from 'lodash-es';
import { formatDateTime, formatLocalDateValue } from '@/utils/datetime';
import type { LedgerLogVo } from '../typings/collection';
import { changeFieldLabels } from './collectionChange';
import { collectionNatureOptions } from './collectionNature';
import { eraMeanings, eraNotations } from './collectionDescription';
import { formatYuan } from './moneyYuan';

const labels: Record<string, string> = {
  ...changeFieldLabels,
  initialDraft: '初次登记资料', batchImport: '批量导入', statutoryChange: '信息变更',
  setRelations: '套件关系', legacyMigration: '历史迁移', legacyMigrationVerification: '历史登记核验', totalRegNo: '总登记号',
  name: '名称', classNo: '分类号', auxNo: '原藏号', categoryId: '文物分类 ID',
  catalogStatus: '编目审核状态', lifeStatus: '藏品状态', orgId: '收藏单位 ID',
  warehouseId: '库位 ID', rfidTag: 'RFID 标签', inventoryHold: '盘点锁定',
  entryVoucherNo: '入馆凭证号', deregVoucherNo: '退出馆藏凭证号',
  historicalConfirmedAt: '历史登记复核时间', originalDateMissingReason: '原登记日期缺项说明', originalRegistrarMissingReason: '原登记人缺项说明', classificationMissingReason: '分类号缺项说明', declaredVerification: '原系统核验声明',
  registeredAt: '正式登记时间', paperLedgerBookNo: '纸质总账册号', paperLedgerPage: '纸质总账页码',
  appraisalInstitution: '鉴定机构', appraisers: '鉴定人', appraisedOn: '鉴定日期',
  coverIsFrontView: '封面为正面照片', eraMeaning: '年代类型', eraNotation: '年代表示方式',
  dimensionUnknownReason: '无法测量尺寸原因', weightUnknownReason: '无法称重原因', quantityUnknownReason: '无法计数原因',
  weightExcludesAccessories: '称重不含附件', measurementMethod: '测量说明', measuredOn: '测量日期', measuredBy: '测量人',
  parts: '分件测量', provenance: '流传经历', excavations: '出土出水记录', publications: '著录文献', manufacture: '制作信息',
  partNo: '分件号', partName: '分件名称', includesAccessories: '包含附件', from: '开始日期', to: '结束日期',
  periodDescription: '时间描述', institution: '机构', collector: '收藏者', originalNumber: '原编号', evidence: '依据',
  date: '日期', site: '地点', position: '位置', publishedOn: '发表日期', publisher: '出版机构', title: '标题',
  chapter: '章节', pages: '页码', summary: '摘要', period: '制作年代', place: '制作地点', technique: '工艺', maker: '制作者',
  height: '通高', mouth: '口径', bottom: '底径', belly: '腹径', length: '长', width: '宽', diameter: '直径', extra: '其他尺寸',
  kind: '类型', condition: '状况', id: '记录 ID', childCollectionId: '分件藏品 ID', relation: '关系', sort: '排序',
  batchId: '迁移批次', sourceSystem: '来源系统', sourceRecordId: '来源记录 ID', originalOrgCode: '原收藏单位代码',
  originalRegisteredAt: '原登记时间', originalRegisteredBy: '原登记人', registrationEvidence: '登记依据文件 ID',
  collectionSnapshot: '核验藏品快照', basisHash: '核验内容摘要', evidenceHash: '原件内容摘要', matchesOriginalLedger: '原账一致性确认', recordedBy: '操作人', recordedAt: '操作时间', reviewedBy: '复核人', reviewedAt: '复核时间', opinion: '复核意见', submittedBy: '提交人', submittedAt: '提交时间', status: '办理状态', content: '核验材料',
  verification: '登记核验', historyVerified: '历史核验完成', migratedAt: '迁移时间', migratedBy: '迁移人 ID', reason: '原因',
};
export const ledgerFieldLabel = (field?: string) => field ? labels[field] || field : '总登记号';

export const ledgerValueDicts: Record<string, string> = {
  level: 'col_relic_level', texture: 'col_texture', source: 'col_source', completeness: 'col_completeness',
  preserveState: 'col_preserve', catalogStatus: 'col_catalog_status', lifeStatus: 'col_life_status', relation: 'col_set_relation',
};
const dateFields = new Set(['acquireDate', 'entryDate', 'appraisedOn', 'measuredOn', 'from', 'to', 'date', 'publishedOn']);
const timeFields = new Set(['receiptCompletedAt', 'registeredAt', 'originalRegisteredAt', 'migratedAt', 'recordedAt', 'reviewedAt', 'submittedAt']);

// Plain identifiers and numbers must retain leading zeroes and their original precision.
function parseValue(raw: unknown): unknown {
  if (typeof raw !== 'string' || !/^[\[{\"]/.test(raw.trim())) return raw;
  try {
    return JSON.parse(raw, (_key, value) => {
      if (typeof value === 'number' && !Number.isSafeInteger(value) && Number.isInteger(value)) throw new Error('Unsafe integer');
      return value;
    });
  } catch { return raw; }
}

type Leaf = { path: string; field: string; label: string; value: unknown };
export type LedgerValueRow = Omit<Leaf, 'value'> & { before: unknown; after: unknown };

function leaves(value: unknown, field: string, path = '', label = ''): Leaf[] {
  // Jackson may retain a LocalDate as [year, month, day] in stored snapshots.
  if (value !== null && typeof value === 'object' && !dateFields.has(field) && !timeFields.has(field)) {
    const entries = Object.entries(value);
    if (!entries.length) return [];
    return entries.flatMap(([key, child]) => {
      const array = Array.isArray(value);
      const childField = array ? field : key;
      const childLabel = array ? `第 ${Number(key) + 1} 项` : ledgerFieldLabel(key);
      return leaves(child, childField, `${path}/${key}`, [label, childLabel].filter(Boolean).join(' / '));
    });
  }
  return [{ path, field, label, value }];
}

/** Both sides use the same changed paths, so additions and removals stay visible. */
export function ledgerValueRows(log: Pick<LedgerLogVo, 'field' | 'before' | 'after'>): LedgerValueRow[] {
  const field = log.field || 'totalRegNo';
  const before = parseValue(log.before), after = parseValue(log.after);
  const structured = (v: unknown) => v !== null && typeof v === 'object';
  if (!structured(before) && !structured(after)) return [{ path: '', field, label: '', before, after }];
  const left = new Map(leaves(before, field).map(item => [item.path, item]));
  const right = new Map(leaves(after, field).map(item => [item.path, item]));
  const rows = [...new Set([...left.keys(), ...right.keys()])].map(path => ({
    ...((left.get(path) || right.get(path))!), before: left.get(path)?.value, after: right.get(path)?.value,
  }));
  const changes = rows.filter(row => !isEqual(row.before ?? null, row.after ?? null));
  return changes.length ? changes : rows.filter(row => row.before != null || row.after != null);
}

export function ledgerValueText(field: string, value: unknown): string {
  if (value == null || value === '') return '未填写';
  if (typeof value === 'boolean' || (field === 'inventoryHold' && (value === 'true' || value === 'false'))) {
    return value === true || value === 'true' ? '是' : '否';
  }
  const options = field === 'nature' ? collectionNatureOptions : field === 'eraMeaning' ? eraMeanings : field === 'eraNotation' ? eraNotations : [];
  const option = options.find(item => item.value === value);
  if (option) return option.label;
  if (field === 'status') return ({ DRAFT: '材料草稿', SUBMITTED: '待独立复核', REJECTED: '已退回', APPROVED: '复核通过', WITHDRAWN: '已撤回' } as Record<string, string>)[String(value)] || String(value);
  if (field === 'verification') return ({ CONFIRMED: '登记已核实', UNVERIFIED: '登记待核实' } as Record<string, string>)[String(value)] || String(value);
  if (dateFields.has(field) || timeFields.has(field)) {
    // Collection API timestamps are milliseconds, including zero and dates near 1970.
    const milliseconds = typeof value === 'number' || (typeof value === 'string' && /^-?\d+$/.test(value.trim()));
    const text = dateFields.has(field) ? formatLocalDateValue(value)
      : milliseconds ? formatLocalDateValue(value, 'YYYY-MM-DD HH:mm:ss') : formatDateTime(value);
    if (text !== '--') return text;
  }
  if (field === 'valuation' && typeof value === 'number') return formatYuan(value);
  return String(value);
}
